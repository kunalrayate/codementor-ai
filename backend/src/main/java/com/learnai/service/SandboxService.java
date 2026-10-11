package com.learnai.service;

import com.learnai.dto.Dtos.RunResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

/**
 * Runs untrusted student code.
 *  - docker: short-lived container, no network, CPU/RAM/PID limits, dropped capabilities, read-only filesystem, time limit (secure).
 *  - local : javac/java as a separate OS process with timeout + heap limit and a source-code blocklist. NOT isolated - development only.
 *  - auto (default): uses Docker when Docker is running and the image is present, otherwise falls back to local.
 */
@Service
@Slf4j
public class SandboxService {
  static final String DOCKER_HELP = "Docker is not running. Start Docker Desktop and wait until it shows 'Engine running', then try again. "
      + "(Or set SANDBOX_MODE=auto / local to run code without Docker on your own machine.)";
  private static final List<String> BLOCKED = List.of("Runtime.getRuntime", "ProcessBuilder", "java.io.File", "FileReader", "FileWriter",
      "FileInputStream", "FileOutputStream", "java.nio.file", "Files.", "java.net", "Socket", "URLConnection", "HttpClient", "ClassLoader",
      "java.lang.reflect", "Class.forName", "System.getenv", "System.setProperty", "System.load", "Unsafe");

  @Value("${app.sandbox.mode}") private String mode;
  @Value("${app.sandbox.work-dir}") private String workDir;
  @Value("${app.sandbox.image}") private String image;
  @Value("${app.sandbox.compile-timeout-sec}") private int compileTimeout;
  @Value("${app.sandbox.run-timeout-sec}") private int runTimeout;
  private volatile long checkedAt;
  private volatile boolean dockerOk;
  private volatile boolean pulling;

  public String configuredMode() { return mode == null ? "auto" : mode.toLowerCase(); }

  /** true when code will currently run in a Docker container. */
  public boolean useDocker() {
    String m = configuredMode();
    if (m.equals("local")) return false;
    if (m.equals("docker")) return true;
    return dockerReady();
  }
  public String mode() { return useDocker() ? "docker" : "local"; }

  /** Source-code blocklist, applied only in local mode. */
  public String forbidden(String code) {
    for (String b : BLOCKED) if (code.contains(b)) return b;
    return null;
  }

  private boolean dockerReady() {
    long now = System.currentTimeMillis();
    if (now - checkedAt < 15_000) return dockerOk;
    synchronized (this) {
      if (System.currentTimeMillis() - checkedAt < 15_000) return dockerOk;
      boolean ok = false;
      if (quick(List.of("docker", "info", "--format", "{{.ServerVersion}}"), 6)) {
        if (quick(List.of("docker", "image", "inspect", image), 8)) ok = true;
        else if (!pulling) { // fetch the image in the background; local mode is used until it is ready
          pulling = true;
          Thread t = new Thread(() -> {
            quick(List.of("docker", "pull", image), 900);
            pulling = false;
            checkedAt = 0;
          });
          t.setDaemon(true);
          t.start();
        }
      }
      dockerOk = ok;
      checkedAt = System.currentTimeMillis();
      return ok;
    }
  }

  private boolean quick(List<String> cmd, int sec) {
    try {
      ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
      pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
      Process p = pb.start();
      if (!p.waitFor(sec, TimeUnit.SECONDS)) { p.destroyForcibly(); return false; }
      return p.exitValue() == 0;
    } catch (Exception e) { return false; }
  }

  public Path prepare(String code) throws IOException {
    Path root = Path.of(workDir);
    Files.createDirectories(root);
    Path dir = Files.createTempDirectory(root, "sbx-");
    Files.writeString(dir.resolve("Main.java"), code, StandardCharsets.UTF_8);
    try { // the container user (uid 1000) must be able to write the compiled class
      Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwxrwxrwx"));
      Files.setPosixFilePermissions(dir.resolve("Main.java"), PosixFilePermissions.fromString("rw-r--r--"));
    } catch (UnsupportedOperationException ignored) { }
    return dir;
  }

  public RunResult compile(Path dir) {
    if (!useDocker()) {
      String javac = javaBin("javac");
      if (javac == null) return new RunResult(false, "javac not found. Start the backend with a JDK (not a JRE).", false);
      return exec(List.of(javac, "-encoding", "UTF-8", "-d", dir.toAbsolutePath().toString(), dir.resolve("Main.java").toAbsolutePath().toString()),
          null, null, compileTimeout);
    }
    String name = "sbx-c-" + UUID.randomUUID();
    List<String> cmd = new ArrayList<>(List.of("docker", "run", "--rm", "--name", name, "--network", "none",
        "--memory", "512m", "--cpus", "1", "--pids-limit", "256", "--user", "1000:1000",
        "--cap-drop", "ALL", "--security-opt", "no-new-privileges",
        "-v", dir.toAbsolutePath() + ":/work", "-w", "/work", image, "javac", "-encoding", "UTF-8", "Main.java"));
    return exec(cmd, null, name, compileTimeout);
  }

  public RunResult run(Path dir, String input) {
    if (!useDocker()) {
      String java = javaBin("java");
      if (java == null) return new RunResult(false, "java launcher not found", false);
      return exec(List.of(java, "-Xmx64m", "-Xss512k", "-XX:+UseSerialGC", "-cp", dir.toAbsolutePath().toString(), "Main"), input, null, runTimeout);
    }
    String name = "sbx-r-" + UUID.randomUUID();
    List<String> cmd = new ArrayList<>(List.of("docker", "run", "--rm", "-i", "--name", name, "--network", "none",
        "--memory", "192m", "--cpus", "0.5", "--pids-limit", "64", "--user", "1000:1000",
        "--cap-drop", "ALL", "--security-opt", "no-new-privileges", "--read-only", "--tmpfs", "/tmp:rw,size=16m",
        "-v", dir.toAbsolutePath() + ":/work:ro", "-w", "/work", image,
        "java", "-Xmx64m", "-Xss512k", "-XX:-UsePerfData", "Main"));
    return exec(cmd, input, name, runTimeout);
  }

  public void cleanup(Path dir) {
    if (dir == null) return;
    try (Stream<Path> s = Files.walk(dir)) {
      s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
    } catch (IOException e) { log.warn("cleanup failed for {}", dir); }
  }

  private String javaBin(String tool) {
    boolean win = System.getProperty("os.name", "").toLowerCase().contains("win");
    Path p = Path.of(System.getProperty("java.home"), "bin", tool + (win ? ".exe" : ""));
    return Files.exists(p) ? p.toString() : null;
  }

  private RunResult exec(List<String> cmd, String input, String containerName, int timeoutSec) {
    try {
      Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
      CompletableFuture<String> out = CompletableFuture.supplyAsync(() -> readLimited(p.getInputStream()));
      try (OutputStream os = p.getOutputStream()) {
        if (input != null) os.write((input.endsWith("\n") ? input : input + "\n").getBytes(StandardCharsets.UTF_8));
      } catch (IOException ignored) { }
      if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) {
        p.destroyForcibly();
        if (containerName != null) kill(containerName);
        return new RunResult(false, "Time limit exceeded", true);
      }
      return new RunResult(p.exitValue() == 0, friendly(out.get(3, TimeUnit.SECONDS), p.exitValue() == 0), false);
    } catch (Exception e) {
      String m = e.getMessage() == null ? "" : e.getMessage();
      if (containerName != null) return new RunResult(false, DOCKER_HELP + "\nDetails: " + m, false);
      return new RunResult(false, "Execution error: " + m, false);
    }
  }

  private String friendly(String out, boolean ok) {
    if (ok || out == null) return out;
    String l = out.toLowerCase();
    if (l.contains("failed to connect to the docker api") || l.contains("cannot connect to the docker daemon")
        || l.contains("error during connect") || l.contains("is the docker daemon running") || l.contains("docker: command not found"))
      return DOCKER_HELP;
    return out;
  }

  private void kill(String name) {
    try { new ProcessBuilder("docker", "kill", name).redirectErrorStream(true).start().waitFor(5, TimeUnit.SECONDS); }
    catch (Exception ignored) { }
  }

  private static String readLimited(InputStream in) {
    try {
      byte[] buf = in.readNBytes(20_000);
      in.transferTo(OutputStream.nullOutputStream()); // drain the rest
      return new String(buf, StandardCharsets.UTF_8);
    } catch (IOException e) { return ""; }
  }
}

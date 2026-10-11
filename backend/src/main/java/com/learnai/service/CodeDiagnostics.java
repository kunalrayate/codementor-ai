package com.learnai.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Turns compiler / runtime output into plain-English explanations (no AI needed). */
public final class CodeDiagnostics {
  private static final Pattern JAVAC = Pattern.compile("Main\\.java:(\\d+): error: (.*)");
  private static final Pattern EXC = Pattern.compile("Exception in thread \"main\" ([\\w.$]+)(?::\\s*(.*))?");
  private static final Pattern AT = Pattern.compile("at Main\\.[\\w$<>]+\\(Main\\.java:(\\d+)\\)");
  private CodeDiagnostics() {}

  /** Removes machine-specific folders so messages read "Main.java:7: error: ...". */
  public static String clean(String out) {
    return out == null ? "" : out.replace("\r", "").replaceAll("(?m)^.*?[\\\\/](Main\\.java)", "$1");
  }

  public static List<String> explain(String raw, boolean timedOut) {
    List<String> out = new ArrayList<>();
    String s = clean(raw);
    if (timedOut || s.contains("Time limit exceeded")) {
      out.add("Time limit exceeded: your program ran too long. Look for an infinite loop (a loop condition that never becomes false) or a very slow approach.");
      return out;
    }
    Matcher m = JAVAC.matcher(s);
    int n = 0;
    while (m.find() && n < 5) { out.add("Line " + m.group(1) + ": " + m.group(2) + "  ->  " + compileHint(m.group(2))); n++; }
    if (n > 0) return out;
    Matcher e = EXC.matcher(s);
    if (e.find()) {
      String type = e.group(1);
      String msg = e.group(2);
      Matcher at = AT.matcher(s);
      String line = at.find() ? " at line " + at.group(1) : "";
      out.add("Runtime error: " + simple(type) + (msg != null && !msg.isBlank() ? " (" + msg.trim() + ")" : "") + line + "  ->  " + runtimeHint(simple(type)));
      return out;
    }
    if (s.contains("Could not find or load main class") || s.contains("no main method"))
      out.add("Java could not start your program. The class must be named Main and contain: public static void main(String[] args).");
    else if (s.contains("OutOfMemoryError")) out.add("Your program used too much memory. Avoid creating huge arrays or endless collections.");
    else if (!s.isBlank()) out.add("The program stopped with an error. Read the message above carefully.");
    return out;
  }

  private static String simple(String type) { return type.substring(type.lastIndexOf('.') + 1); }

  private static String compileHint(String msg) {
    String m = msg.toLowerCase();
    if (m.contains("';' expected")) return "a semicolon is missing at the end of a statement (check this line and the one above it)";
    if (m.contains("cannot find symbol")) return "a variable, method or class is misspelled or was never declared (check capital letters and imports)";
    if (m.contains("incompatible types")) return "you are using the wrong type of value (for example a double where an int is needed, or a String instead of a number)";
    if (m.contains("missing return statement")) return "every path of a method that returns a value must end with return";
    if (m.contains("reached end of file") || m.contains("class, interface, enum, or record expected")) return "curly braces { } do not match - check every opening and closing brace";
    if (m.contains("should be declared in a file named")) return "the public class must be named Main";
    if (m.contains("might not have been initialized")) return "give the variable a starting value before you use it";
    if (m.contains("not a statement")) return "this line is not a complete Java statement";
    if (m.contains("unreported exception")) return "wrap the call in try/catch or add 'throws' to the method";
    if (m.contains("illegal start of expression")) return "a bracket or semicolon is probably missing just above this line";
    if (m.contains("cannot be applied") || m.contains("no suitable method")) return "the method is called with the wrong number or type of arguments";
    if (m.contains("already defined")) return "this variable name is declared twice in the same scope";
    return "read the compiler message and fix this line";
  }

  private static String runtimeHint(String t) {
    return switch (t) {
      case "NoSuchElementException", "InputMismatchException" -> "your program read input that is missing or in a different format than expected (check how many values you read and use nextInt/next/nextLine correctly)";
      case "ArrayIndexOutOfBoundsException", "StringIndexOutOfBoundsException", "IndexOutOfBoundsException" -> "you used an index outside the valid range 0 to length-1";
      case "NullPointerException" -> "you used an object that is null (it was never created)";
      case "ArithmeticException" -> "you divided by zero";
      case "NumberFormatException" -> "text could not be converted to a number";
      case "StackOverflowError" -> "recursion never stops - add or fix the base case";
      case "ClassCastException" -> "an object was cast to the wrong type";
      case "NegativeArraySizeException" -> "an array was created with a negative size";
      default -> "read the exception message and the line number";
    };
  }
}

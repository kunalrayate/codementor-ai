package com.learnai.web;

import com.learnai.common.*;
import com.learnai.dto.Dtos.*;
import com.learnai.model.*;
import com.learnai.repo.*;
import com.learnai.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {
  private final RagService rag;
  private final DocumentRepository docs;
  private final CourseRepository courses;
  private final EnrollmentRepository enrollments;
  private final Access access;
  @Value("${app.uploads-dir}") private String uploadsDir;

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public DocumentDto upload(@AuthenticationPrincipal User u, @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
                            @RequestParam String title, @RequestParam(required = false) Long courseId) throws IOException {
    String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
    if (!name.endsWith(".pdf")) throw ApiException.badRequest("Only PDF files are supported");
    Course c = null;
    if (courseId != null) {
      c = courses.findById(courseId).orElseThrow(() -> ApiException.notFound("Course"));
      if (u.getRole() == Role.STUDENT) { if (!enrollments.existsByStudentAndCourse(u, c)) throw ApiException.forbidden("Not enrolled"); c = null; }
      else access.requireOwnerOrAdmin(u, c); // only staff can publish materials to a whole course
    }
    byte[] bytes = file.getBytes();
    LearningDocument d = rag.ingest(u, title, c, bytes);
    Path dir = Path.of(uploadsDir);
    Files.createDirectories(dir);
    Path f = dir.resolve("doc-" + d.getId() + ".pdf");
    Files.write(f, bytes);                       // keep the original so it can be viewed later
    d.setFilePath(f.toString());
    docs.save(d);
    return dto(u, d);
  }

  @GetMapping
  public List<DocumentDto> list(@AuthenticationPrincipal User u) {
    return docs.findAllByOrderByIdDesc().stream().filter(d -> canView(u, d)).map(d -> dto(u, d)).toList();
  }

  /** Extracted text of the document (what the AI Tutor searches). */
  @GetMapping("/{id}/content")
  public Map<String, Object> content(@AuthenticationPrincipal User u, @PathVariable Long id) {
    LearningDocument d = visible(u, id);
    String text = rag.fullText(d);
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", d.getId());
    m.put("title", d.getTitle());
    m.put("hasFile", d.getFilePath() != null);
    m.put("length", text.length());
    m.put("text", text.length() > 60_000 ? text.substring(0, 60_000) : text);
    return m;
  }

  /** The original PDF, for the in-page viewer. */
  @GetMapping("/{id}/file")
  public ResponseEntity<byte[]> file(@AuthenticationPrincipal User u, @PathVariable Long id) throws IOException {
    LearningDocument d = visible(u, id);
    if (d.getFilePath() == null || !Files.exists(Path.of(d.getFilePath()))) throw ApiException.notFound("Original file");
    return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"document-" + id + ".pdf\"")
        .body(Files.readAllBytes(Path.of(d.getFilePath())));
  }

  @DeleteMapping("/{id}")
  public void delete(@AuthenticationPrincipal User u, @PathVariable Long id) throws IOException {
    LearningDocument d = visible(u, id);
    boolean allowed = d.getUploadedBy().getId().equals(u.getId()) || u.getRole() == Role.ADMIN
        || (d.getCourse() != null && access.isOwnerOrAdmin(u, d.getCourse()));
    if (!allowed) throw ApiException.forbidden("You cannot delete this document");
    if (d.getFilePath() != null) Files.deleteIfExists(Path.of(d.getFilePath()));
    docs.delete(d);
  }

  private LearningDocument visible(User u, Long id) {
    LearningDocument d = docs.findById(id).orElseThrow(() -> ApiException.notFound("Document"));
    if (!canView(u, d)) throw ApiException.forbidden("You cannot view this document");
    return d;
  }

  private boolean canView(User u, LearningDocument d) {
    return d.getUploadedBy().getId().equals(u.getId()) || u.getRole() == Role.ADMIN
        || (d.getCourse() != null && (access.isOwnerOrAdmin(u, d.getCourse()) || enrollments.existsByStudentAndCourse(u, d.getCourse())));
  }

  private DocumentDto dto(User u, LearningDocument d) {
    return new DocumentDto(d.getId(), d.getTitle(), d.getCourse() == null ? null : d.getCourse().getId(), d.getFilePath() != null,
        d.getUploadedBy().getId().equals(u.getId()));
  }
}

package com.learnai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnai.common.ApiException;
import com.learnai.model.*;
import com.learnai.repo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/**
 * Retrieval-Augmented Generation: PDF -> text -> chunks -> embeddings (JSON in MySQL) -> cosine search.
 * If the AI provider has no embedding endpoint (e.g. Groq), it automatically falls back to keyword search,
 * so PDF Q&A keeps working with any provider.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RagService {
  private static final Set<String> STOP = Set.of("the", "and", "for", "are", "with", "what", "how", "why", "who", "does", "this", "that",
      "from", "about", "into", "can", "you", "your", "was", "were", "have", "has", "explain", "tell");
  private final AiSettingsService settings;
  private final DocumentRepository docs;
  private final DocumentChunkRepository chunks;
  private final ObjectMapper mapper;
  @Value("${app.rag.max-chunks}") private int maxChunks;

  @Transactional
  public LearningDocument ingest(User uploader, String title, Course course, byte[] pdf) {
    String text;
    try (PDDocument d = Loader.loadPDF(pdf)) {
      text = new PDFTextStripper().getText(d);
    } catch (Exception e) {
      throw ApiException.badRequest("Could not read the PDF file");
    }
    List<String> parts = chunk(text, 800, 100);
    if (parts.isEmpty()) throw ApiException.badRequest("No text found in the PDF (scanned PDFs need OCR)");
    if (parts.size() > maxChunks) parts = parts.subList(0, maxChunks);

    LearningDocument doc = new LearningDocument();
    doc.setTitle(title);
    doc.setCourse(course);
    doc.setUploadedBy(uploader);
    docs.save(doc);
    boolean embeddings = settings.configured();
    for (String p : parts) {
      DocumentChunk c = new DocumentChunk();
      c.setDocument(doc);
      c.setContent(p);
      String emb = "";
      if (embeddings) {
        try {
          emb = mapper.writeValueAsString(settings.embeddingModel().embed(p));
        } catch (Exception e) {
          embeddings = false;
          log.warn("Embeddings unavailable ({}). Falling back to keyword search for this document.", e.getMessage());
        }
      }
      c.setEmbedding(emb);
      chunks.save(c);
    }
    return doc;
  }

  public List<String> search(User u, String query, Long courseId, int k) {
    List<DocumentChunk> candidates = chunks.visible(u.getId(), courseId);
    if (candidates.isEmpty()) return List.of();
    float[] q = null;
    boolean allEmbedded = candidates.stream().allMatch(c -> c.getEmbedding() != null && !c.getEmbedding().isBlank());
    if (allEmbedded && settings.configured()) {
      try { q = settings.embeddingModel().embed(query); } catch (Exception e) { q = null; }
    }
    Set<String> terms = terms(query);
    record Scored(String text, double score) {}
    List<Scored> scored = new ArrayList<>();
    for (DocumentChunk c : candidates) {
      double s;
      try {
        s = (q != null) ? cosine(q, mapper.readValue(c.getEmbedding(), float[].class)) : keyword(c.getContent(), terms);
      } catch (Exception e) { s = 0; }
      scored.add(new Scored(c.getContent(), s));
    }
    return scored.stream().filter(x -> x.score() > 0)
        .sorted(Comparator.comparingDouble(Scored::score).reversed()).limit(k).map(Scored::text).toList();
  }

  public String documentText(User u, Long documentId, int maxChars) {
    LearningDocument d = docs.findById(documentId).orElseThrow(() -> ApiException.notFound("Document"));
    boolean mine = d.getUploadedBy().getId().equals(u.getId());
    if (!mine && u.getRole() == Role.STUDENT && d.getCourse() == null) throw ApiException.forbidden("Not your document");
    StringBuilder sb = new StringBuilder();
    for (DocumentChunk c : chunks.findByDocument(d)) {
      sb.append(c.getContent()).append("\n");
      if (sb.length() >= maxChars) break;
    }
    return sb.length() > maxChars ? sb.substring(0, maxChars) : sb.toString();
  }

  static Set<String> terms(String query) {
    Set<String> out = new LinkedHashSet<>();
    for (String w : query.toLowerCase().split("[^a-z0-9]+")) if (w.length() >= 3 && !STOP.contains(w)) out.add(w);
    return out;
  }

  static double keyword(String content, Set<String> terms) {
    String lower = content.toLowerCase();
    double hits = 0;
    for (String t : terms) {
      int idx = 0;
      while ((idx = lower.indexOf(t, idx)) >= 0) { hits++; idx += t.length(); }
    }
    return hits / Math.sqrt(lower.length() + 1);
  }

  static List<String> chunk(String text, int size, int overlap) {
    String t = text == null ? "" : text.replaceAll("\\s+", " ").trim();
    List<String> out = new ArrayList<>();
    for (int i = 0; i < t.length(); i += size - overlap) {
      out.add(t.substring(i, Math.min(t.length(), i + size)));
      if (i + size >= t.length()) break;
    }
    return out;
  }

  static double cosine(float[] a, float[] b) {
    double dot = 0, na = 0, nb = 0;
    for (int i = 0; i < Math.min(a.length, b.length); i++) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i]; }
    return (na == 0 || nb == 0) ? 0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
  }

  /** Whole extracted text of a document (the 100-char chunk overlap is removed). */
  public String fullText(LearningDocument d) {
    StringBuilder sb = new StringBuilder();
    boolean first = true;
    for (DocumentChunk c : chunks.findByDocumentOrderByIdAsc(d)) {
      String t = c.getContent() == null ? "" : c.getContent();
      sb.append(first ? t : (t.length() > 100 ? t.substring(100) : ""));
      first = false;
    }
    return sb.toString();
  }
}

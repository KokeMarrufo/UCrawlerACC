package finder.universities;

/**
 * One searchable page. UBC rows and the empty university slots share this shape:
 * university, program id, name, URL, and text.
 */
public record SearchDocument(
        String universityId,
        String programId,
        String name,
        String url,
        String text,
        boolean savedPage) {
}

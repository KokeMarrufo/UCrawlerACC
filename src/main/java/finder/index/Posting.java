package finder.index;

/** One occurrence of a token: which URL, and the token's position in that page. */
public record Posting(String url, int position) {}

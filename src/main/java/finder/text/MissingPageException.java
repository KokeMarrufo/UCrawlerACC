package finder.text;

import java.io.IOException;
import java.nio.file.Path;

public class MissingPageException extends IOException {
    public MissingPageException(Path path) {
        super("Missing page file: " + path);
    }
}

package ast;

import java.io.BufferedOutputStream;
import java.io.IOException;

/**
 * Created by Shane on 18/04/16.
 */
public interface Wast {

    void write(BufferedOutputStream out) throws IOException;

    void write(BufferedOutputStream out, int indent) throws IOException;

    default void indent(BufferedOutputStream out, int indent) throws IOException {
        for (int i = 0; i < indent; i++) {
            out.write(" ".getBytes());
        }
    }

}

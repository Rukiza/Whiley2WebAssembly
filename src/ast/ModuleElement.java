package ast;

import java.io.BufferedOutputStream;
import java.io.IOException;

/**
 * Created by Shane on 18/04/16.
 */
public interface ModuleElement extends Wast {

    public abstract class Type implements ModuleElement {

    }

    public class SType extends Type {

        @Override
        public void write(BufferedOutputStream out) throws IOException {

        }

        @Override
        public void write(BufferedOutputStream out, int indent) throws IOException {

        }
    }

    public abstract class Import implements ModuleElement {

    }

    public class SImport extends Import {

        @Override
        public void write(BufferedOutputStream out) throws IOException {

        }

        @Override
        public void write(BufferedOutputStream out, int indent) throws IOException {

        }
    }

    public abstract class Export implements ModuleElement {

        private final String string;
        private final ExprElement.Var var;
        private final Memory memory;

        public Export (String string, ExprElement.Var var) {
            this.string = string;
            this.var = var;
            this.memory = null;
        }

        public Export (String string, Memory memory) {
            this.memory = memory;
            this.string = string;
            this.var = null;
        }

        public String getString() {
            return string;
        }

        public ExprElement.Var getVar() {
            return var;
        }

        public Memory getMemory() {
            return memory;
        }
    }

    public class SExport extends Export {

        public SExport(String string, ExprElement.Var var) {
            super(string, var);
        }

        public SExport(String string, Memory memory) {
            super(string, memory);
        }

        @Override //TODO: Add other path
        public void write(BufferedOutputStream out) throws IOException {
            out.write("( export ".getBytes());
            if (getVar() != null) {
                out.write(getString().getBytes());
                out.write(" ".getBytes());
                getVar().write(out);
            }
            out.write(" )".getBytes());
        }

        @Override
        public void write(BufferedOutputStream out, int indent) throws IOException {
            indent(out, indent);
            write(out);
        }
    }

    public abstract class Start implements ModuleElement {

    }

    public class SStart extends Start {

        @Override
        public void write(BufferedOutputStream out) throws IOException {

        }

        @Override
        public void write(BufferedOutputStream out, int indent) throws IOException {

        }
    }

    public abstract class Table implements ModuleElement {

    }

    public class STable extends Table {

        @Override
        public void write(BufferedOutputStream out) throws IOException {

        }

        @Override
        public void write(BufferedOutputStream out, int indent) throws IOException {

        }
    }

    public abstract class Memory implements ModuleElement {

    }

    public class SMemory extends Memory {

        @Override
        public void write(BufferedOutputStream out) throws IOException {

        }

        @Override
        public void write(BufferedOutputStream out, int indent) throws IOException {

        }
    }

    public abstract class Segment implements ModuleElement {

    }

    public class SSegment extends Segment {

        @Override
        public void write(BufferedOutputStream out) throws IOException {

        }

        @Override
        public void write(BufferedOutputStream out, int indent) throws IOException {

        }
    }
}

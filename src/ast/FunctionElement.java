package ast;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Created by Shane on 18/04/16.
 */
public interface FunctionElement extends Wast{

    public abstract class Type implements FunctionElement {

        private final ExprElement.Var var;

        public Type(ExprElement.Var var) {
            this.var = var;
        }

        public ExprElement.Var getVar() {
            return var;
        }
    }

    class SType extends Type {

        public SType(ExprElement.Var var) {
            super(var);
        }

        @Override
        public void write(BufferedOutputStream out) throws IOException {

        }

        @Override
        public void write(BufferedOutputStream out, int indent) throws IOException {

        }
    }

    abstract class Param implements FunctionElement {

        private final List<ExprElement.Type> types;
        private final String name;
        private final ExprElement.Type type;

        public Param (String name, ExprElement.Type type) {
            this.type = type;
            this.name = name;
            this.types = null;
        }

        public Param (List<ExprElement.Type> types) {
            this.types = types;
            this.name = null;
            this.type = null;
        }

        public List<ExprElement.Type> getTypes() {
            return types;
        }

        public String getName() {
            return name;
        }

        public ExprElement.Type getType() {
            return type;
        }
    }

    class SParam extends Param {


        public SParam(String name, ExprElement.Type type) {
            super(name, type);
        }

        public SParam(List<ExprElement.Type> types) {
            super(types);
        }

        @Override
        public void write(BufferedOutputStream out) throws IOException {
            out.write("( param ".getBytes());
            if (getName() != null) {
                out.write(getName().getBytes());
                out.write(" ".getBytes());
                getType().write(out);
            }
            out.write(" )".getBytes());
        }

        @Override
        public void write(BufferedOutputStream out, int indent) throws IOException {
            indent(out, indent);
            write(out);
        }
    }

    abstract class Result implements FunctionElement {

        private final ExprElement.Type type;

        public Result(ExprElement.Type type) {
            this.type = type;
        }

        public ExprElement.Type getType() {
            return type;
        }
    }

    class SResult extends Result {

        public SResult(ExprElement.Type type) {
            super(type);
        }

        @Override
        public void write(BufferedOutputStream out) throws IOException {
            out.write("( result ".getBytes());
            getType().write(out);
            out.write(" )".getBytes());
        }

        @Override
        public void write(BufferedOutputStream out, int indent) throws IOException {
            indent(out, indent);
            write(out);
        }
    }

    abstract class Local implements FunctionElement {


        private final List<ExprElement.Type> types;
        private final String name;
        private final ExprElement.Type type;

        public Local (String name, ExprElement.Type type) {
            this.type = type;
            this.name = name;
            this.types = null;
        }

        public Local (List<ExprElement.Type> types) {
            this.types = types;
            this.name = null;
            this.type = null;
        }

        public List<ExprElement.Type> getTypes() {
            return types;
        }

        public String getName() {
            return name;
        }

        public ExprElement.Type getType() {
            return type;
        }

    }

    class SLocal extends Local {

        public SLocal(String name, ExprElement.Type type) {
            super(name, type);
        }

        public SLocal(List<ExprElement.Type> types) {
            super(types);
        }

        @Override
        public void write(BufferedOutputStream out) throws IOException {
            out.write("( local ".getBytes());
            out.write(getName().getBytes());
            out.write(" ".getBytes());
            getType().write(out);
            out.write(" )".getBytes());
        }

        @Override
        public void write(BufferedOutputStream out, int indent) throws IOException {
            indent(out, indent);
            write(out);
        }
    }

}

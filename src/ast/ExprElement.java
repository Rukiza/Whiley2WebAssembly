package ast;

import java.io.BufferedOutputStream;
import java.io.IOException;

/**
 * Created by Shane on 18/04/16.
 */
public interface ExprElement extends Wast{

    abstract class Type implements ExprElement {

        private final String type;

        public Type (String type){
            this.type = type;
        }

        public String getType() {
            return type;
        }
    }

    class SType extends Type {

        public SType(String type) {
            super(type);
        }

        @Override
        public void write(BufferedOutputStream out) throws IOException {
            out.write(getType().getBytes());
        }

        @Override
        public void write(BufferedOutputStream out, int indent) throws IOException {
            indent(out, indent);
            write(out);
        }
    }

    abstract class Var implements ExprElement {

        private final String name;
        private final Integer index;

        public Var (String name) {
            this.index = null;
            this.name = name;
        }

        public Var (Integer index) {
            this.index = index;
            this.name = null;
        }

        public String getName() {
            return name;
        }

        public Integer getIndex() {
            return index;
        }
    }

    public class SVar extends Var {

        public SVar(String name) {
            super(name);
        }

        public SVar(Integer index) {
            super(index);
        }

        @Override
        public void write(BufferedOutputStream out) throws IOException {
            if (getIndex() != null) {
                out.write(getIndex().toString().getBytes());
            }
            else {
                out.write(getName().getBytes());
            }
        }

        @Override
        public void write(BufferedOutputStream out, int indent) throws IOException {
            indent(out, indent);
            write(out);
        }
    }

    abstract class Value implements ExprElement {

        private final Integer intValue;
        private final Float floatValue;

        public Value (Integer intValue) {
            this.intValue = intValue;
            this.floatValue = null;
        }

        public Value (Float floatValue) {
            this.floatValue = floatValue;
            this.intValue = null;
        }

        public Integer getIntValue() {
            return intValue;
        }

        public Float getFloatValue() {
            return floatValue;
        }
    }

    public class SValue extends Value {

        public SValue(Integer intValue) {
            super(intValue);
        }

        public SValue(Float floatValue) {
            super(floatValue);
        }

        @Override
        public void write(BufferedOutputStream out) throws IOException {
            if (getFloatValue() != null) {
                out.write(getFloatValue().toString().getBytes());
            } else {
                out.write(getIntValue().toString().getBytes());
            }
        }

        @Override
        public void write(BufferedOutputStream out, int indent) throws IOException {
            indent(out, indent);
            write(out);
        }
    }

}

package ast;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.util.List;

public class SFunction extends Function {


    public SFunction(String name,
                     FunctionElement.Type type,
                     List<FunctionElement.Param> params,
                     FunctionElement.Result result,
                     List<FunctionElement.Local> locals,
                     List<Expr> exprs) {
        super(name, type, params, result, locals, exprs);
    }

    @Override
    public void write(BufferedOutputStream out) throws IOException {
        //write(out, 4);
    }

    @Override
    public void write(BufferedOutputStream out, int indent) throws IOException {
        indent(out, indent);
        out.write("( func".getBytes());
        if (getName() != null) {
            out.write(" ".getBytes());
            out.write(getName().getBytes());
        }
        if (getType() != null) {
            out.write(" ".getBytes());
            getType().write(out);
        }
        for (FunctionElement.Param param: getParams()) {
            out.write(" ".getBytes());
            param.write(out);
        }
        if (getResult() != null) {
            out.write(" ".getBytes());
            getResult().write(out);
        }
        out.write("\n".getBytes());
        for (FunctionElement.Local local: getLocals()) {
            local.write(out, indent+4);
            out.write("\n".getBytes());
        }
        for (Expr expr: getExprs()) {
            expr.write(out, indent+4);
            out.write("\n".getBytes());
        }
        indent(out,indent);
        out.write(")\n".getBytes());
    }
}

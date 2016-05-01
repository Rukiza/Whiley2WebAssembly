package ast;

import java.io.BufferedOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Shane on 18/04/16.
 */

public abstract class Function implements ModuleElement {

    private final String name;
    private final FunctionElement.Type type;
    private final List<FunctionElement.Param> params;
    private final FunctionElement.Result result;
    private final List<FunctionElement.Local> locals;
    private final List<Expr> exprs;

    public Function(String name,
                     FunctionElement.Type type,
                     List<FunctionElement.Param> params,
                     FunctionElement.Result result,
                     List<FunctionElement.Local> locals,
                     List<Expr> exprs) {
        this.name = name;
        this.type = type;
        this.params = params == null ? new ArrayList<>() : new ArrayList<>(params);
        this.result = result;
        this.locals = locals == null ? new ArrayList<>() : new ArrayList<>(locals);
        this.exprs = exprs == null ? new ArrayList<>() : new ArrayList<>(exprs);
    }

    public String getName() {
        return name;
    }

    public FunctionElement.Type getType() {
        return type;
    }

    public List<FunctionElement.Param> getParams() {
        return params;
    }

    public FunctionElement.Result getResult() {
        return result;
    }

    public List<FunctionElement.Local> getLocals() {
        return locals;
    }

    public List<Expr> getExprs() {
        return exprs;
    }


}


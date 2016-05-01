package util;

import ast.*;

import java.util.List;

/**
 * Created by Shane on 19/04/16.
 */
public interface WastFactory {

    public Module createModule (List<ModuleElement.Type> type,
                                List<Function> funcs,
                                List<ModuleElement.Import> imports,
                                List<ModuleElement.Export> exports,
                                List<ModuleElement.Table> tables,
                                ModuleElement.Memory memory,
                                ModuleElement.Start start);

    public ModuleElement.Export createExport (String string, ExprElement.Var var);

    public ModuleElement.Export createExport (String string, ModuleElement.Memory memory);

    public Function createFunction (String name,
                                    FunctionElement.Type type,
                                    List<FunctionElement.Param> params,
                                    FunctionElement.Result result,
                                    List<FunctionElement.Local> locals,
                                    List<Expr> exprs);

    public FunctionElement.Type createFunctionType (ExprElement.Var var);

    public FunctionElement.Param createParam (String name, ExprElement.Type type);

    public FunctionElement.Result createResult (ExprElement.Type type);

    public FunctionElement.Local createLocal (String name, ExprElement.Type type);

    //Expressions

    public Expr.Nop createNop ();

    public Expr.Block createBlock (String name, List<Expr> exprs);

    public Expr.Loop createLoop (String nameOne, String nameTwo, List<Expr> expers);

    public Expr.Select createSelect (Expr exprOne, Expr exprTwo, Expr exprThree);

    public Expr.If createIf (Expr condition, String thenLabel, List<Expr> thenExprs, String elseLabel, List<Expr> elseExprs);

    public Expr.Br createBr (ExprElement.Var var, Expr expr);

    public Expr.BrIf createBrIf (ExprElement.Var var, Expr expr, Expr condition);

    public Expr.BrTable createBrTable (ExprElement.Var varOne, ExprElement.Var varTwo, Expr exprOne, Expr exprTwo);

    public Expr.Return createReturn (Expr expr);

    public Expr.Call createCall (ExprElement.Var var, List<Expr> expr);

    public Expr.CallImport createCallImport (ExprElement.Var var, List<Expr> exprs);

    public Expr.CallIndirect crateCallIndirect (ExprElement.Var var, Expr expr, List<Expr> exprs);

    public Expr.GetLocal createGetLocal (ExprElement.Var var);

    public Expr.SetLocal createSetLocal (ExprElement.Var var, Expr expr);

    public Expr.Load createLoad (ExprElement.Type type, String sign, Integer offset, Integer align, Expr expr);

    public Expr.Store createStore (ExprElement.Type type, Integer offset, Integer align, Expr location, Expr value);

    public Expr.Const createConst (ExprElement.Type type, ExprElement.Value value);

    public Expr.UnOp createUnOp (ExprElement.Type type, String op, Expr expr);

    public Expr.BinOp createBinOp (ExprElement.Type type, String op, Expr argOne, Expr argTwo);

    public Expr.Unreachable createUnreachable ();

    //Missing some Expers that are not being used


    //Expression Elements

    public ExprElement.Type createExprType (String type);

    public ExprElement.Var createVar (Integer integer);

    public ExprElement.Var createVar (String name);

    public ExprElement.Value createValue (Integer value);

    public ExprElement.Value createValue (Float value);


    public class SWastFactory implements WastFactory{


        @Override
        public Module createModule(List<ModuleElement.Type> type,
                                   List<Function> funcs,
                                   List<ModuleElement.Import> imports,
                                   List<ModuleElement.Export> exports,
                                   List<ModuleElement.Table> tables,
                                   ModuleElement.Memory memory,
                                   ModuleElement.Start start) {
            return new SModule(type, funcs, imports, exports, tables, memory, start);
        }

        @Override
        public ModuleElement.Export createExport(String string, ExprElement.Var var) {
            return new ModuleElement.SExport(string, var);
        }

        @Override
        public ModuleElement.Export createExport(String string, ModuleElement.Memory memory) {
            return new ModuleElement.SExport(string, memory);
        }

        @Override
        public Function createFunction(String name, FunctionElement.Type type, List<FunctionElement.Param> params, FunctionElement.Result result, List<FunctionElement.Local> locals, List<Expr> exprs) {
            return new SFunction(name, type, params, result, locals, exprs);
        }

        @Override
        public FunctionElement.Type createFunctionType(ExprElement.Var var) {
            return new FunctionElement.SType(var);
        }

        @Override
        public FunctionElement.Param createParam(String name, ExprElement.Type type) {
            return new FunctionElement.SParam(name, type);
        }

        @Override
        public FunctionElement.Result createResult(ExprElement.Type type) {
            return new FunctionElement.SResult(type);
        }

        @Override
        public FunctionElement.Local createLocal(String name, ExprElement.Type type) {
            return new FunctionElement.SLocal(name, type);
        }

        @Override
        public Expr.Nop createNop() {
            return new Expr.SNop();
        }

        @Override
        public Expr.Block createBlock(String name, List<Expr> exprs) {
            return new Expr.SBlock(name, exprs);
        }

        @Override
        public Expr.Loop createLoop(String nameOne, String nameTwo, List<Expr> expers) {
            return new Expr.SLoop(nameOne, nameTwo, expers);
        }

        @Override
        public Expr.Select createSelect(Expr exprOne, Expr exprTwo, Expr exprThree) {
            return new Expr.SSelect(exprOne, exprTwo, exprThree);
        }

        @Override
        public Expr.If createIf(Expr condition, String thenLabel, List<Expr> thenExprs, String elseLabel, List<Expr> elseExprs) {
            return new Expr.SIf(condition, thenLabel, thenExprs, elseLabel, elseExprs);
        }

        @Override
        public Expr.Br createBr(ExprElement.Var var, Expr expr) {
            return new Expr.SBr(var, expr);
        }

        @Override
        public Expr.BrIf createBrIf(ExprElement.Var var, Expr expr, Expr condition) {
            return new Expr.SBrIf(var, expr, condition);
        }

        @Override
        public Expr.BrTable createBrTable(ExprElement.Var varOne, ExprElement.Var varTwo, Expr exprOne, Expr exprTwo) {
            return new Expr.SBrTable(varOne, varTwo, exprOne, exprTwo);
        }

        @Override
        public Expr.Return createReturn(Expr expr) {
            return new Expr.SReturn(expr);
        }

        @Override
        public Expr.Call createCall(ExprElement.Var var, List<Expr> expr) {
            return new Expr.SCall(var, expr);
        }

        @Override
        public Expr.CallImport createCallImport(ExprElement.Var var, List<Expr> exprs) {
            return new Expr.SCallImport(var, exprs);
        }

        @Override
        public Expr.CallIndirect crateCallIndirect(ExprElement.Var var, Expr expr, List<Expr> exprs) {
            return new Expr.SCallIndirect(var, expr, exprs);
        }

        @Override
        public Expr.GetLocal createGetLocal(ExprElement.Var var) {
            return new Expr.SGetLocal(var);
        }

        @Override
        public Expr.SetLocal createSetLocal(ExprElement.Var var, Expr expr) {
            return new Expr.SSetLocal(var, expr);
        }

        @Override
        public Expr.Load createLoad(ExprElement.Type type, String sign, Integer offset, Integer align, Expr expr) {
            return new Expr.SLoad(type, sign, offset, align, expr);
        }

        @Override
        public Expr.Store createStore(ExprElement.Type type, Integer offset, Integer align, Expr location, Expr value) {
            return new Expr.SStore(type, offset, align, location, value);
        }

        @Override
        public Expr.Const createConst(ExprElement.Type type, ExprElement.Value value) {
            return new Expr.SConst(type, value);
        }

        @Override
        public Expr.UnOp createUnOp(ExprElement.Type type, String op, Expr expr) {
            return new Expr.SUnOp(type, op, expr);
        }

        @Override
        public Expr.BinOp createBinOp(ExprElement.Type type, String op, Expr argOne, Expr argTwo) {
            return new Expr.SBinOp(type, op, argOne, argTwo);
        }

        @Override
        public Expr.Unreachable createUnreachable() {
            return new Expr.SUnreachable();
        }

        @Override
        public ExprElement.Type createExprType(String type) {
            return new ExprElement.SType(type);
        }

        @Override
        public ExprElement.Var createVar(Integer integer) {
            return new ExprElement.SVar(integer);
        }

        @Override
        public ExprElement.Var createVar(String name) {
            return new ExprElement.SVar(name);
        }

        @Override
        public ExprElement.Value createValue(Integer value) {
            return new ExprElement.SValue(value);
        }

        @Override
        public ExprElement.Value createValue(Float value) {
            return new ExprElement.SValue(value);
        }
    }

}



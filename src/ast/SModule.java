package ast;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.util.List;

public class SModule extends Module {

    public SModule(List<ModuleElement.Type> type,
                   List<Function> funcs,
                   List<ModuleElement.Import> imports,
                   List<ModuleElement.Export> exports,
                   List<ModuleElement.Table> tables,
                   ModuleElement.Memory memory,
                   ModuleElement.Start start) {
        super(type, funcs, imports, exports, tables, memory, start);
    }

    @Override
    public void write(BufferedOutputStream out) throws IOException {
        //write(out, 0);
    }

    @Override
    public void write(BufferedOutputStream out, int indent) throws IOException {
        indent(out, indent);
        out.write("( module\n".getBytes());
        for (ModuleElement.Type type: getType()) {
            type.write(out, indent+4);
            out.write("\n".getBytes());
        }
        for (Function function: getFuncs()) {
            function.write(out, indent+4);
            out.write("\n".getBytes());
        }
        for (ModuleElement.Import imports: getImports()) {
            imports.write(out, indent+4);
            out.write("\n".getBytes());
        }
        for (ModuleElement.Export export: getExports()) {
            export.write(out, indent+4);
            out.write("\n".getBytes());
        }
        for (ModuleElement.Table table: getTables()) {
            table.write(out, indent+4);
            out.write("\n".getBytes());
        }
        if (getMemory() != null) {
            getMemory().write(out, indent+4);
            out.write("\n".getBytes());
        }
        if (getStart() != null) {
            getStart().write(out, indent+4);
            out.write("\n".getBytes());
        }
        indent(out, indent);
        out.write(")".getBytes());
    }

}

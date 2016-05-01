package ast;

import java.io.BufferedOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Shane on 18/04/16.
 */

public abstract class Module implements Wast {

    public final List<ModuleElement.Type> type;
    public final List<Function> funcs;
    public final List<ModuleElement.Import> imports;
    public final List<ModuleElement.Export> exports;
    public final List<ModuleElement.Table> tables;
    public final ModuleElement.Memory memory;
    public final ModuleElement.Start start;

    public Module(List<ModuleElement.Type> type,
                  List<Function> funcs,
                  List<ModuleElement.Import> imports,
                  List<ModuleElement.Export> exports,
                  List<ModuleElement.Table> tables,
                  ModuleElement.Memory memory,
                  ModuleElement.Start start) {
        this.type = type == null ? new ArrayList<>() : new ArrayList<>(type);
        this.funcs = funcs == null ? new ArrayList<>() : new ArrayList<>(funcs);
        this.imports = imports == null ? new ArrayList<>() : new ArrayList<>(imports);
        this.exports = exports == null ? new ArrayList<>() : new ArrayList<>(exports);
        this.tables = tables == null ? new ArrayList<>() : new ArrayList<>(tables);
        this.memory = memory;
        this.start = start;
    }

    public List<ModuleElement.Type> getType() {
        return type;
    }

    public List<Function> getFuncs() {
        return funcs;
    }

    public List<ModuleElement.Import> getImports() {
        return imports;
    }

    public List<ModuleElement.Export> getExports() {
        return exports;
    }

    public List<ModuleElement.Table> getTables() {
        return tables;
    }

    public ModuleElement.Memory getMemory() {
        return memory;
    }

    public ModuleElement.Start getStart() {
        return start;
    }


}


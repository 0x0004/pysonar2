package org.yinwang.pysonar.types;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yinwang.pysonar.Indexer;
import org.yinwang.pysonar.Scope;
import org.yinwang.pysonar.ast.FunctionDef;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FunType extends Type {

    @NotNull
    private List<Arrow> arrows = new ArrayList<>();
    public FunctionDef func;
    @Nullable
    public ClassType cls = null;
    private Scope env;
    @Nullable
    public Type selfType;                 // self's type for calls
    public List<Type> defaultTypes;       // types for default parameters (evaluated at def time)


    public FunType() {
    }


    public FunType(FunctionDef func, Scope env) {
        this.func = func;
        this.env = env;
    }


    public FunType(Type from, Type to) {
        addMapping(from, to);
        getTable().addSuper(Indexer.idx.builtins.BaseFunction.getTable());
        getTable().setPath(Indexer.idx.builtins.BaseFunction.getTable().getPath());
    }


    public void addMapping(Type from, Type to) {
        arrows.add(new Arrow(from, to));
    }


    @Nullable
    public Type getMapping(@NotNull Type from) {
        for (Arrow a : arrows) {
            if (from.equals(a.from)) {
                return a.to;
            }
        }
        return null;
    }


    public Type getReturnType() {
        if (!arrows.isEmpty()) {
            return arrows.get(0).to;
        } else {
            return Indexer.idx.builtins.unknown;
        }
    }


    public FunctionDef getFunc() {
        return func;
    }

    public Scope getEnv() {
        return env;
    }

    @Nullable
    public ClassType getCls() {
        return cls;
    }

    public void setCls(ClassType cls) {
        this.cls = cls;
    }

    @Nullable
    public Type getSelfType() {
        return selfType;
    }

    public void setSelfType(Type selfType) {
        this.selfType = selfType;
    }

    public void clearSelfType() {
        this.selfType = null;
    }

    public List<Type> getDefaultTypes() {
        return defaultTypes;
    }

    public void setDefaultTypes(List<Type> defaultTypes) {
        this.defaultTypes = defaultTypes;
    }


    @Override
    public boolean equals(Object other) {
        if (other instanceof FunType) {
            FunType fo = (FunType) other;
            return fo.getTable().getPath().equals(getTable().getPath()) || this == other;
        } else {
            return false;
        }
    }


    static Type removeNoneReturn(@NotNull Type toType) {
        if (toType.isUnionType()) {
            Set<Type> types = new HashSet<>(toType.asUnionType().getTypes());
            types.remove(Indexer.idx.builtins.Cont);
            return UnionType.newUnion(types);
        } else {
            return toType;
        }
    }


    @Override
    public int hashCode() {
        return "FunType".hashCode();
    }


    @Override
    protected String printType(@NotNull CyclicTypeRecorder ctr) {
        if (arrows.isEmpty()) {
            return "? -> ?";
        }

        StringBuilder sb = new StringBuilder();

        Integer num = ctr.visit(this);
        if (num != null) {
            sb.append("#").append(num);
        } else {
            int newNum = ctr.push(this);

            int i = 0;
            for (Arrow a : arrows) {
                sb.append(a.from.printType(ctr));
                sb.append(" -> ");
                sb.append(a.to.printType(ctr));
                if (i < arrows.size() - 1) {
                    sb.append(" | ");
                }
                i++;
            }

            if (ctr.isUsed(this)) {
                sb.append("=#").append(newNum).append(": ");
            }
            ctr.pop(this);
        }
        return sb.toString();
    }
}

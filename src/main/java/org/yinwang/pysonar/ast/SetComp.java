package org.yinwang.pysonar.ast;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yinwang.pysonar.Scope;
import org.yinwang.pysonar.types.ListType;
import org.yinwang.pysonar.types.Type;

import java.util.List;

public class SetComp extends Node {

    public Node elt;
    public List<Comprehension> generators;


    public SetComp(Node elt, List<Comprehension> generators, int start, int end) {
        super(start, end);
        this.elt = elt;
        this.generators = generators;
        addChildren(elt);
        addChildren(generators);
    }


    @NotNull
    @Override
    public Type resolve(Scope s, int tag) {
        resolveList(generators, s, tag);
        return new ListType(resolveExpr(elt, s, tag));
    }

    @NotNull
    @Override
    public String toString() {
        return "<NSetComp:" + start + ":" + elt + ">";
    }

    @Override
    public void visit(@NotNull NodeVisitor v) {
        if (v.visit(this)) {
            visitNode(elt, v);
            visitNodeList(generators, v);
        }
    }
}

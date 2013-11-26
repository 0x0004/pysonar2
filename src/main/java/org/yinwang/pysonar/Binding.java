package org.yinwang.pysonar;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yinwang.pysonar.ast.Node;
import org.yinwang.pysonar.types.ModuleType;
import org.yinwang.pysonar.types.Type;

import java.util.LinkedHashSet;
import java.util.Set;


public class Binding
{
    public enum Kind
    {
        ATTRIBUTE,  // attr accessed with "." on some other object
        CLASS,  // class definition
        CONSTRUCTOR,  // __init__ functions in classes
        FUNCTION,  // plain function
        METHOD,  // static or instance method
        MODULE,  // file
        PARAMETER,  // function param
        SCOPE,  // top-level variable ("scope" means we assume it can have attrs)
        VARIABLE  // local variable
    }


    // The indexer is heavily memory-constrained, so these sets are initially
    // small.  The vast majority of bindings have only one definition.
    private static final int DEF_SET_INITIAL_CAPACITY = 1;
    private static final int REF_SET_INITIAL_CAPACITY = 1;

    // yinw: C-style bit-field changed to straightfoward booleans.
    // The compiler should compact the space unless it is stupid.
    private boolean isStatic = false;         // static fields/methods
    private boolean isSynthetic = false;      // auto-generated bindings
    private boolean isReadonly = false;       // non-writable attributes
    private boolean isDeprecated = false;     // documented as deprecated
    private boolean isBuiltin = false;        // not from a source file

    @NotNull
    private String name;    // unqualified name
    @NotNull
    private String qname;   // qualified name
    private Type type;     // inferred type
    public Kind kind;      // name usage context

    @NotNull
    private Set<Def> defs;   // definitions (may be multiple)
    private Set<Ref> refs;


    public Binding(@NotNull String id, Node node, @NotNull Type type, @NotNull Kind kind)
    {
        this.name = id;
        this.qname = type.getTable().getPath();
        this.type = type;
        this.kind = kind;
        this.defs = new LinkedHashSet<>(DEF_SET_INITIAL_CAPACITY);
        addDef(node);

        Indexer.idx.registerBinding(this);
    }


    @NotNull
    public String getName()
    {
        return name;
    }


    public void setQname(String qname)
    {
        this.qname = qname;
    }


    @NotNull
    public String getQname()
    {
        return qname;
    }


    public void addDef(@Nullable Node node)
    {
        if (node != null)
        {
            Def def = new Def(node, this);
            addDef(def);
        }
    }


    public void addDef(Def def)
    {
        def.setBinding(this);

        Set<Def> defs = getDefs();
        defs.add(def);
        if (def.isURL())
        {
            markBuiltin();
        }
    }


    public void addRef(Ref ref)
    {
        getRefs().add(ref);
    }


    // Returns one definition (even if there are many)
    @NotNull
    public Def getSingle()
    {
        return getDefs().iterator().next();
    }


    public void setType(Type type)
    {
        this.type = type;
    }


    public Type getType()
    {
        return type;
    }


    public void setKind(Kind kind)
    {
        this.kind = kind;
    }


    public Kind getKind()
    {
        return kind;
    }


    public void markStatic()
    {
        isStatic = true;
    }


    public boolean isStatic()
    {
        return isStatic;
    }


    public void markSynthetic()
    {
        isSynthetic = true;
    }


    public boolean isSynthetic()
    {
        return isSynthetic;
    }


    public void markReadOnly()
    {
        isReadonly = true;
    }


    public boolean isReadOnly()
    {
        return isReadonly;
    }


    public boolean isDeprecated()
    {
        return isDeprecated;
    }


    public void markDeprecated()
    {
        isDeprecated = true;
    }


    public boolean isBuiltin()
    {
        return isBuiltin;
    }


    public void markBuiltin()
    {
        isBuiltin = true;
    }


    @NotNull
    public Set<Def> getDefs()
    {
        return defs;
    }


    @NotNull
    public Def getDef()
    {
        return defs.iterator().next();
    }


    public Set<Ref> getRefs()
    {
        if (refs == null)
        {
            refs = new LinkedHashSet<>(REF_SET_INITIAL_CAPACITY);
        }
        return refs;
    }


    @NotNull
    public String getFirstFile()
    {
        Type bt = getType();
        if (bt instanceof ModuleType)
        {
            String file = bt.asModuleType().getFile();
            return file != null ? file : "<built-in module>";
        }

        for (Def def : defs)
        {
            String file = def.getFile();
            if (file != null)
            {
                return file;
            }
        }

        return "<built-in binding>";
    }


    @NotNull
    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("<Binding:");
        sb.append(":qname=").append(qname);
        sb.append(":type=").append(type);
        sb.append(":kind=").append(kind);
        sb.append(":defs=").append(defs);
        sb.append(":refs=");
        if (getRefs().size() > 10)
        {
            sb.append("[");
            sb.append(refs.iterator().next());
            sb.append(", ...(");
            sb.append(refs.size() - 1);
            sb.append(" more)]");
        }
        else
        {
            sb.append(refs);
        }
        sb.append(">");
        return sb.toString();
    }

}

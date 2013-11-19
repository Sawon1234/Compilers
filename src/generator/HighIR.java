import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.Arrays;

class ExprUse extends Triple<HStatement, HExpression, HVar> {

    public ExprUse(HStatement s, HExpression e, HVar v) {
        super(s, e, v);
    }

    public boolean equals(ExprUse e) {
        return e.middle.equals(middle);
    }

    public boolean dependsOn(String name) {
        return middle.dependsOn(name);
    }
}

class AvailExprsList {
    LinkedHashSet<ExprUse> list;

    public AvailExprsList() {
        list = new LinkedHashSet<ExprUse>();
    }

    public void add(HStatement s, HExpression e, HVar v) {
        list.add(new ExprUse(s, e, v));
    }

    public AvailExprsList copy() {
        AvailExprsList a = new AvailExprsList();
        a.list = new LinkedHashSet<ExprUse>(list);
        return a;
    }

    public boolean contains(ExprUse e) {
        return list.contains(e);
    }

    public void invalidateVar(String name) {
        for (ExprUse e : list) {
            if (e.dependsOn(name)) {
                list.remove(e);
            }
        }
    }
}

abstract class HNode {
    public abstract LNode accept(HLVisitor v);
    public void convertFuns(Map<String, HFunction> map) {
        return;
    }
}

abstract class HExpression extends HNode {

    public static int curVar = 0;

    HVar newExpr;

    public boolean joinExpr(AvailExprsList avail, HStatement stmt) {
        for (ExprUse t : avail.list) {
            if (this.equals(t.middle)) {
                // if this hasn't been assigned to a var yet, assign it
                if (t.right == null) {
                    HVar uniqVar = new HVar("_" + curVar);
                    curVar++;
                    HStatement assign = new HAssign(uniqVar.var, t.middle);
                    stmt.newDeclarations.stmts.add(assign);
                    t.right = uniqVar;
                    newExpr = uniqVar;
                    return true;
                }
                else {
                    newExpr = t.right;
                }
            }
        }
        avail.add(stmt, this, null);
        return false;
    }

    public abstract void cse(AvailExprsList avail, HStatement stmt);

    public abstract boolean dependsOn(String name);
}

abstract class HStatement extends HNode {
    HBlock newDeclarations = new HBlock(new ArrayList<HStatement>());
    public abstract void cse(AvailExprsList avail);
}

class HInterface extends HNode {
    static HashMap<String, HInterface> classes = new HashMap<String, HInterface>(); // a set of all the classes
    String name;
    HashMap<String, HFunction> funs;
    List<String> parents;
    int id;
    List<String> superInterfaces;

    public HInterface(int id, String name, HashMap<String,HFunction> funs, List<String> parents) {
        this.id = id;
        this.name = name;
        this.funs = funs;
        this.parents = parents;
    }

    List<String> getSuperInterfaces() {
        // no more super classes
        if (parents.size() == 1 && parents.contains("Thing")) {
            return new ArrayList<String>();
        }
        if (superInterfaces != null) {
            return superInterfaces;
        }
        superInterfaces = new ArrayList<String>();
        for (String s : parents) {
            HInterface i = classes.get(s);
            if (!(i instanceof HClass)) {
                superInterfaces.add(s);
            }
            HInterface superInterface = classes.get(s);
            if (superInterface != null) {
                superInterfaces.addAll(superInterface.getSuperInterfaces());
            }
        }
        // System.out.println(superInterfaces);
        return superInterfaces;
    }

    void implementSuperInterfaces(HashMap<String, HInterface> classes) {
        for (String s : getSuperInterfaces()) {
            HInterface i = classes.get(s);
            if (i != null) {
                for (Map.Entry<String,HFunction> f : funs.entrySet()) {
                    i.addImplementation(id, f.getValue());
                }
            }
        }
    }

    void addImplementation(Integer id, HFunction f) {
        HFunction fun = funs.get(f.name);
        if (fun != null) {
            fun.addDef(id, f);
        }
    }

    public String toString() {
        StringBuilder s = new StringBuilder();
        for (Map.Entry<String, HFunction> f : funs.entrySet()) {
            s.append(f.getValue().toString() + "\n");
        }
        return s.toString();
    }

    public LNode accept(HLVisitor v) {
        return v.visit(this);
    }

    public void convertFuns(Map<String, HFunction> map) {
        for (Map.Entry<String,HFunction> f : funs.entrySet()) {
            f.getValue().convertFuns(map);
        }
    }

    public void cse(AvailExprsList avail) {
        for (Map.Entry<String, HFunction> e : funs.entrySet()) {
            e.getValue().cse(avail);
        }
    }
}

class HClass extends HInterface {

    List<String> fields;
    HExpression parent;
    CubexTypeContext tCont;
    List<HStatement> stmts;
    List<HExpression> exprs;

    public HClass(int id, String name, List<HExpression> exprs, 
        List<HStatement> stmts, HashMap<String,HFunction> funs, List<String> parents, 
        List<String> fields, HExpression parent, CubexTypeContext tCont) {
        super(id, name, funs, parents);
        this.stmts = stmts;
        this.exprs = exprs;
        this.fields = fields;
        this.parent = parent;
        this.tCont = tCont;
    }
    public LNode accept(HLVisitor v) {
        return v.visit(this);
    }

    public void convertFuns(Map<String, HFunction> map) {
        for (Map.Entry<String,HFunction> f : funs.entrySet()) {
            f.getValue().convertFuns(map);
        }
        for (HStatement s : stmts) {
            s.convertFuns(map);
        }
        for (HExpression e : exprs) {
            e.convertFuns(map);
        }
    }

    public void cse(AvailExprsList avail) {
        for (HStatement s : stmts) {
            s.cse(avail);
        }

        // need to do cse for the constructor args
    }
}

class HConditional extends HStatement {

    HExpression expr;
    HStatement stmt1;
    HStatement stmt2;

    public HConditional(HExpression expr, HStatement stmt1, HStatement stmt2) {
        this.expr = expr;
        this.stmt1 = stmt1;
        this.stmt2 = stmt2;
    }

    public LNode accept(HLVisitor v) {
        return v.visit(this);
    }

    public void convertFuns(Map<String, HFunction> map) {
        expr.convertFuns(map);
        stmt1.convertFuns(map);
        stmt2.convertFuns(map);
    }

    // public Map<HAssign, String> cse(Map<HAssign, String> avail) {
    //     Map<HAssign, String> s1 = stmt1.cse(avail);
    //     Map<HAssign, String> s2 = stmt2.cse(avail);

    //     for (Map.Entry<HAssign, String> e : stmt1.entrySet()) {
    //         if (e.getValue() == ) 
    //     }
    // }
    public void cse(AvailExprsList avail) {
        expr.cse(avail, this);
        AvailExprsList avail1 = avail.copy();
        AvailExprsList avail2 = avail.copy();
        stmt1.cse(avail1);
        stmt2.cse(avail2);
        // combine the lists
        for (ExprUse t : avail1.list) {
            if (avail2.contains(t)) {
                avail.add(t.left, t.middle, t.right);
            }
        }
    }
}

class HForLoop extends HStatement {
    String name;
    HExpression expr;
    HStatement stmt;

    public HForLoop(String name, HExpression expr, HStatement stmt) {
        this.name = name;
        this.expr = expr;
        this.stmt = stmt;
    }

    public LNode accept(HLVisitor v) {
        return v.visit(this);
    }

    public void convertFuns(Map<String, HFunction> map) {
        expr.convertFuns(map);
        stmt.convertFuns(map);
    }

    public void cse(AvailExprsList avail) {
        expr.cse(avail, this);
        AvailExprsList avail1 = avail.copy();
        stmt.cse(avail1);
    }
}

class HWhileLoop extends HStatement {

    HExpression expr;
    HStatement stmt;

    public HWhileLoop(HExpression expr, HStatement stmt) {
        this.expr = expr;
        this.stmt = stmt;
    }

    public LNode accept(HLVisitor v) {
        return v.visit(this);
    }

    public void convertFuns(Map<String, HFunction> map) {
        expr.convertFuns(map);
        stmt.convertFuns(map);
    }

    public void cse(AvailExprsList avail) {
        expr.cse(avail, this);
        AvailExprsList avail1 = avail.copy();
        stmt.cse(avail1);
    }    
}

class HReturn extends HStatement {

    HExpression expr;

    public HReturn(HExpression expr) {
        this.expr = expr;
    }

    public LNode accept(HLVisitor v) {
        return v.visit(this);
    }

    public void convertFuns(Map<String, HFunction> map) {
        expr.convertFuns(map);
    }

    public void cse(AvailExprsList avail) {
        expr.cse(avail, this);
    }
}

class HBlock extends HStatement {

    List<HStatement> stmts;

    public HBlock(List<HStatement> stmts) {
        this.stmts = stmts;
    }

    public LNode accept(HLVisitor v) {
        return v.visit(this);
    }

    public void convertFuns(Map<String, HFunction> map) {
        for (HStatement s : stmts) {
            s.convertFuns(map);
        }
    }

    public void cse(AvailExprsList avail) {
        for (HStatement s : stmts) {
            s.cse(avail);
        }
    }
}

class HAssign extends HStatement {

    HExpression expr;
    String name;

    public HAssign(String name, HExpression expr) {
        this.expr = expr;
        this.name = name;
    }

    public LNode accept(HLVisitor v) {
        return v.visit(this);
    }

    public void convertFuns(Map<String, HFunction> map) {
        expr.convertFuns(map);
    }

    public void cse(AvailExprsList avail) {
        expr.cse(avail, this);
        avail.invalidateVar(name);
    }
}

class HFunction {
    String name;
    String declassedName;
    List<String> args;
    HStatement body;

    public HFunction(String name, HStatement body, List<String> args) {
        this.name = name;
        this.body = body;
        this.args = args;
    }

    void addDef(Integer id, HFunction f) {
        return;
    }

    public String toString() {
        return declassedName;
    }

    public LNode accept(HLVisitor v) {
        return v.visit(this);
    }

    public void convertFuns(Map<String, HFunction> map) {
        if (body != null) {
            body.convertFuns(map);
        }
    }

    public void cse(AvailExprsList avail) {
        AvailExprsList availcopy = avail.copy();
        body.cse(availcopy);
    }
}

class HUndefFunction extends HFunction {
    HashMap<Integer, HFunction> defs = new HashMap<Integer, HFunction>();

    public HUndefFunction(String name, List<String> args) {
        super(name, null, args);
    }

    void addDef(Integer id, HFunction f) {
        defs.put(id, f);
    }

    public String toString() {
        return declassedName + "\n" + defs.toString();
    }

    public LNode accept(HLVisitor v) {
        return v.visit(this);
    }
}

class HFunctionCall extends HExpression {

    String name;
    List<HExpression> args;

    public HFunctionCall(String name, List<HExpression> args) {
        this.name = name;
        this.args = args;
    }

    public LNode accept(HLVisitor v) {
        return v.visit(this);
    }

    public void convertFuns(Map<String, HFunction> map) {
        for (HExpression e : args) {
            e.convertFuns(map);
        }
        HFunction f = map.get(name);
        if (f != null) {
            name = f.declassedName;
            List<HExpression> newArgs = new ArrayList<HExpression>();
            newArgs.add(new HVar("_obj"));
            newArgs.addAll(args);
            args = newArgs;
        }
    }

    public boolean equals(HFunctionCall e) {
        if (args.size() != e.args.size())
            return false;
        if (!name.equals(e.name))
            return false;

        for (int i = 0; i < args.size(); i++) {
            if (args.get(i).equals(e.args.get(i))) 
                return false;
        }
        return true;
    }

    public void cse(AvailExprsList avail, HStatement stmt) {
        joinExpr(avail, stmt);
        for (HExpression e : args) {
            e.joinExpr(avail, stmt);
        }
    }

    public boolean dependsOn(String name) {
        for (HExpression e : args) {
            if (e.dependsOn(name))
                return true;
        }
        return false;
    }
}

class HAppend extends HExpression {
    HExpression left;
    HExpression right;

    public HAppend(HExpression left, HExpression right) {
        this.left = left;
        this.right = right;
    }

    public LNode accept(HLVisitor v) {
        return v.visit(this);
    }

    public void convertFuns(Map<String, HFunction> map) {
        left.convertFuns(map);
        right.convertFuns(map);
    }

    public boolean equals(HAppend a) {
        return left.equals(a.left) && right.equals(a.right);
    }

    public void cse(AvailExprsList avail, HStatement stmt) {
        joinExpr(avail, stmt);

        left.joinExpr(avail, stmt);
        right.joinExpr(avail, stmt);
    }

    public boolean dependsOn(String name) {
        return left.dependsOn(name) || right.dependsOn(name);
    }
}

class HIterable extends HExpression {
    List<HExpression> elems;

    public HIterable(List<HExpression> elems) {
        this.elems = elems;
    }

    public LNode accept(HLVisitor v) {
        return v.visit(this);
    }

    public void convertFuns(Map<String, HFunction> map) {
        for (HExpression e : elems) {
            e.convertFuns(map);
        }
    }

    public boolean equals(HIterable h) {
        if (elems.size() != h.elems.size())
            return false;

        for (int i = 0; i < elems.size(); i++) {
            if (!elems.get(i).equals(h.elems.get(i)))
                return false;
        }

        return true;
    }
    public void cse(AvailExprsList avail, HStatement stmt) {
        for (HExpression e : elems) {
            e.joinExpr(avail, stmt);
        }
    }

    public boolean dependsOn(String name) {
        for (HExpression e : elems) {
            if (e.dependsOn(name))
                return true;
        }
        return false;
    }
}

class HBoolean extends HExpression {

    String val;

    public HBoolean(String val) {
        this.val = val;
    } 

    public LNode accept(HLVisitor v) {
        return v.visit(this);
    }

    public void cse(AvailExprsList avail, HStatement stmt) {
        return;
    }

    public boolean dependsOn(String name) {
        return false;
    }
}

class HInt extends HExpression {

    int val;

    public HInt(int val) {
        this.val = val;
    }

    public LNode accept(HLVisitor v) {
        return v.visit(this);
    }

    public boolean equals(HInt h) {
        return val == h.val;
    }

    public void cse(AvailExprsList avail, HStatement stmt) {
        return;
    }

    public boolean dependsOn(String name) {
        return false;
    }
}

class HString extends HExpression {

    String val;

    public HString(String val) {
        this.val = val;
    }

    public LNode accept(HLVisitor v) {
        return v.visit(this);
    }

    public boolean equals(HString h) {
        return val.equals(h.val);
    }

    public void cse(AvailExprsList avail, HStatement stmt) {
        return;
    }

    public boolean dependsOn(String name) {
        return false;
    }
}

class HVar extends HExpression {
    
    String var;

    public HVar(String var) {
        this.var = var;
    }

    public LNode accept(HLVisitor v) {
        return v.visit(this);
    }

    public boolean equals(HVar h) {
        return var.equals(h.var);
    }

    public void cse(AvailExprsList avail, HStatement stmt) {
        return;
    }

    public boolean dependsOn(String name) {
        return var.equals(name);
    }
}

abstract class HProg extends HNode {
    HProg prog;
    public abstract void cse(AvailExprsList avail);
}

class HStatementProg extends HProg {
    List<HStatement> stmts;

    public HStatementProg(List<HStatement> stmts, HProg prog) {
        this.stmts = stmts;
        this.prog = prog;
    }

    public LNode accept(HLVisitor v) {
        return v.visit(this);
    }

    public void cse(AvailExprsList avail) {
        for (HStatement s : stmts) {
            s.cse(avail);
        }
    }
}

class HClassProg extends HProg {

    HInterface cls;

    public HClassProg(HInterface cls, HProg prog) {
        this.cls = cls;
        this.prog = prog;
    }

    public LNode accept(HLVisitor v) {
        return v.visit(this);
    }

    public void cse(AvailExprsList avail) {
        cls.cse(avail);
    }
}

class HFunProg extends HProg {

    List<HFunction> funs;

    public HFunProg(List<HFunction> funs, HProg prog) {
        this.funs = funs;
        this.prog = prog;
    }

    public LNode accept(HLVisitor v) {
        return v.visit(this);
    }

    public void cse(AvailExprsList avail) {
        for (HFunction f : funs) {
            f.cse(avail);
        }
    }
}
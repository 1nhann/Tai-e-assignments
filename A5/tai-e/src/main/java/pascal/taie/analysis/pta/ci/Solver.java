/*
 * Tai-e: A Static Analysis Framework for Java
 *
 * Copyright (C) 2022 Tian Tan <tiantan@nju.edu.cn>
 * Copyright (C) 2022 Yue Li <yueli@nju.edu.cn>
 *
 * This file is part of Tai-e.
 *
 * Tai-e is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * Tai-e is distributed in the hope that it will be useful,but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Tai-e. If not, see <https://www.gnu.org/licenses/>.
 */

package pascal.taie.analysis.pta.ci;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pascal.taie.World;
import pascal.taie.analysis.graph.callgraph.CallGraphs;
import pascal.taie.analysis.graph.callgraph.CallKind;
import pascal.taie.analysis.graph.callgraph.DefaultCallGraph;
import pascal.taie.analysis.graph.callgraph.Edge;
import pascal.taie.analysis.pta.core.heap.HeapModel;
import pascal.taie.analysis.pta.core.heap.Obj;
import pascal.taie.ir.exp.InvokeExp;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.proginfo.MethodRef;
import pascal.taie.ir.stmt.Copy;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.ir.stmt.LoadArray;
import pascal.taie.ir.stmt.LoadField;
import pascal.taie.ir.stmt.New;
import pascal.taie.ir.stmt.StmtVisitor;
import pascal.taie.ir.stmt.StoreArray;
import pascal.taie.ir.stmt.StoreField;
import pascal.taie.language.classes.ClassHierarchy;
import pascal.taie.language.classes.JField;
import pascal.taie.language.classes.JMethod;
import pascal.taie.util.AnalysisException;
import pascal.taie.language.type.Type;

import java.util.List;

class Solver {

    private static final Logger logger = LogManager.getLogger(Solver.class);

    private final HeapModel heapModel;

    private DefaultCallGraph callGraph;

    private PointerFlowGraph pointerFlowGraph;

    private WorkList workList;

    private StmtProcessor stmtProcessor;

    private ClassHierarchy hierarchy;

    Solver(HeapModel heapModel) {
        this.heapModel = heapModel;
    }

    /**
     * Runs pointer analysis algorithm.
     */
    void solve() {
        initialize();
        analyze();
    }

    /**
     * Initializes pointer analysis.
     */
    private void initialize() {
        workList = new WorkList();
        pointerFlowGraph = new PointerFlowGraph();
        callGraph = new DefaultCallGraph();
        stmtProcessor = new StmtProcessor();
        hierarchy = World.get().getClassHierarchy();
        // initialize main method
        JMethod main = World.get().getMainMethod();
        callGraph.addEntryMethod(main);
        addReachable(main);
    }

    /**
     * Processes new reachable method.
     */
    private void addReachable(JMethod method) {
        // TODO - finish me
        if (!callGraph.contains(method)){
            callGraph.addReachableMethod(method);
            method.getIR().stmts().forEach(stmt -> {
                stmt.accept(stmtProcessor);
            });
        }
    }

    /**
     * Processes statements in new reachable methods.
     */
    private class StmtProcessor implements StmtVisitor<Void> {
        // TODO - if you choose to implement addReachable()
        //  via visitor pattern, then finish me


        @Override
        public Void visit(New stmt) {
            Obj obj = heapModel.getObj(stmt);
            Var x = stmt.getLValue();

            Pointer xPointer = pointerFlowGraph.getVarPtr(x);
            PointsToSet pointsToSet = new PointsToSet(obj);
            workList.addEntry(xPointer,pointsToSet);
            return null;
        }

        @Override
        public Void visit(Copy stmt) {
            // x = y
            Var x = stmt.getLValue();
            Var y = stmt.getRValue();
            Pointer xPointer = pointerFlowGraph.getVarPtr(x);
            Pointer yPointer = pointerFlowGraph.getVarPtr(y);
            addPFGEdge(yPointer,xPointer);
            return null;
        }

        @Override
        public Void visit(LoadField stmt) {
            if (stmt.isStatic()){
                // y = T.f
                Pointer y = pointerFlowGraph.getVarPtr(stmt.getLValue());
                JField field = stmt.getFieldAccess().getFieldRef().resolve();
                StaticField Tf = pointerFlowGraph.getStaticField(field);
                addPFGEdge(Tf,y);
            }
            return null;
        }

        @Override
        public Void visit(StoreField stmt) {
            if (stmt.isStatic()){
                // T.f = y
                Pointer y = pointerFlowGraph.getVarPtr(stmt.getRValue());
                JField field = stmt.getFieldAccess().getFieldRef().resolve();
                StaticField Tf = pointerFlowGraph.getStaticField(field);
                addPFGEdge(y,Tf);
            }
            return null;
        }

        @Override
        public Void visit(Invoke stmt) {
            if (stmt.isStatic()){
                // r = T.m(a1,....,an)
                JMethod m = resolveCallee(null,stmt);
                processCallAfterDetermineMethod(stmt,m);
            }
            return null;
        }
    }

    /**
     * Adds an edge "source -> target" to the PFG.
     */
    private void addPFGEdge(Pointer source, Pointer target) {
        // TODO - finish me

        if (!pointerFlowGraph.getSuccsOf(source).contains(target)){
            pointerFlowGraph.addEdge(source,target);
            PointsToSet pts = source.getPointsToSet();
            if (!pts.isEmpty()){
                workList.addEntry(target,pts);
            }
        }
    }

    /**
     * Processes work-list entries until the work-list is empty.
     */
    private void analyze() {
        // TODO - finish me

        while (!workList.isEmpty()){
            WorkList.Entry entry = workList.pollEntry();
            Pointer n = entry.pointer();
            PointsToSet pts = entry.pointsToSet();
            PointsToSet delta = propagate(n,pts);

            if (n instanceof VarPtr x){
                delta.forEach(obj -> {
                    //x.f = y
                    Var xVar = x.getVar();
                    xVar.getStoreFields().forEach(storeField -> {
                        Pointer y = pointerFlowGraph.getVarPtr(storeField.getRValue());
                        JField field = storeField.getFieldAccess().getFieldRef().resolve();
                        InstanceField oif = pointerFlowGraph.getInstanceField(obj,field);
                        addPFGEdge(y,oif);
                    });
                    //y = x.f
                    xVar.getLoadFields().forEach(loadField -> {
                        Pointer y = pointerFlowGraph.getVarPtr(loadField.getLValue());
                        JField field = loadField.getFieldAccess().getFieldRef().resolve();
                        InstanceField oif = pointerFlowGraph.getInstanceField(obj,field);
                        addPFGEdge(oif,y);
                    });

                    //x[i] = y
                    xVar.getStoreArrays().forEach(storeArray -> {
                        Pointer y = pointerFlowGraph.getVarPtr(storeArray.getRValue());
                        ArrayIndex xi = pointerFlowGraph.getArrayIndex(obj);
                        addPFGEdge(y,xi);
                    });

                    //y = x[i]
                    xVar.getLoadArrays().forEach(loadArray -> {
                        Pointer y = pointerFlowGraph.getVarPtr(loadArray.getLValue());
                        ArrayIndex xi = pointerFlowGraph.getArrayIndex(obj);
                        addPFGEdge(xi,y);
                    });



                    //method call
                    processCall(xVar,obj);
                });
            }
        }

    }

    /**
     * Propagates pointsToSet to pt(pointer) and its PFG successors,
     * returns the difference set of pointsToSet and pt(pointer).
     */
    private PointsToSet propagate(Pointer pointer, PointsToSet pointsToSet) {
        // TODO - finish me
//        return null;

        PointsToSet delta = new PointsToSet();
        PointsToSet ptn = pointer.getPointsToSet();
        pointsToSet.forEach(obj -> {
            if (!ptn.contains(obj)){
                delta.addObject(obj);
            }
        });

        if (!delta.isEmpty()){
            delta.forEach(ptn::addObject);
            pointerFlowGraph.getSuccsOf(pointer).forEach(s -> {
                workList.addEntry(s,delta);
            });
        }

        return delta;
    }

    /**
     * Processes instance calls when points-to set of the receiver variable changes.
     *
     * @param var the variable that holds receiver objects
     * @param recv a new discovered object pointed by the variable.
     */
    private void processCall(Var var, Obj recv) {
        // TODO - finish me

        var.getInvokes().forEach(invoke -> {
            JMethod m = resolveCallee(recv,invoke);
            Var thisVar = m.getIR().getThis();
            Pointer thisPointer = pointerFlowGraph.getVarPtr(thisVar);
            workList.addEntry(thisPointer,new PointsToSet(recv));

            processCallAfterDetermineMethod(invoke,m);

        });

    }


    private void processCallAfterDetermineMethod(Invoke invoke,JMethod m){
        if(!callGraph.getCalleesOf(invoke).contains(m)){
            callGraph.addEdge(new Edge<>(CallGraphs.getCallKind(invoke),invoke,m));
            addReachable(m);
            List<Var> params = m.getIR().getParams();
            List<Var> args = invoke.getInvokeExp().getArgs();

            for (int i = 0; i < args.size();i++){
                addPFGEdge(
                        pointerFlowGraph.getVarPtr(args.get(i)),
                        pointerFlowGraph.getVarPtr(params.get(i))
                );
            }
            Var returnVar = invoke.getResult();

            if (returnVar != null){
                List<Var> mReturnVars = m.getIR().getReturnVars();

                mReturnVars.forEach(var1 -> {
                    addPFGEdge(
                            pointerFlowGraph.getVarPtr(var1),
                            pointerFlowGraph.getVarPtr(returnVar)
                    );
                });
            }


        }
    }

    /**
     * Resolves the callee of a call site with the receiver object.
     *
     * @param recv     the receiver object of the method call. If the callSite
     *                 is static, this parameter is ignored (i.e., can be null).
     * @param callSite the call site to be resolved.
     * @return the resolved callee.
     */
    private JMethod resolveCallee(Obj recv, Invoke callSite) {
        Type type = recv != null ? recv.getType() : null;
        return CallGraphs.resolveCallee(type, callSite);
    }

    CIPTAResult getResult() {
        return new CIPTAResult(pointerFlowGraph, callGraph);
    }
}

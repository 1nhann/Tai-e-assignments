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

package pascal.taie.analysis.pta.cs;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pascal.taie.World;
import pascal.taie.analysis.graph.callgraph.CallGraph;
import pascal.taie.analysis.graph.callgraph.CallGraphs;
import pascal.taie.analysis.graph.callgraph.CallKind;
import pascal.taie.analysis.graph.callgraph.Edge;
import pascal.taie.analysis.pta.PointerAnalysisResult;
import pascal.taie.analysis.pta.PointerAnalysisResultImpl;
import pascal.taie.analysis.pta.core.cs.CSCallGraph;
import pascal.taie.analysis.pta.core.cs.context.Context;
import pascal.taie.analysis.pta.core.cs.element.ArrayIndex;
import pascal.taie.analysis.pta.core.cs.element.CSCallSite;
import pascal.taie.analysis.pta.core.cs.element.CSManager;
import pascal.taie.analysis.pta.core.cs.element.CSMethod;
import pascal.taie.analysis.pta.core.cs.element.CSObj;
import pascal.taie.analysis.pta.core.cs.element.CSVar;
import pascal.taie.analysis.pta.core.cs.element.InstanceField;
import pascal.taie.analysis.pta.core.cs.element.MapBasedCSManager;
import pascal.taie.analysis.pta.core.cs.element.Pointer;
import pascal.taie.analysis.pta.core.cs.element.StaticField;
import pascal.taie.analysis.pta.core.cs.selector.ContextSelector;
import pascal.taie.analysis.pta.core.heap.HeapModel;
import pascal.taie.analysis.pta.core.heap.Obj;
import pascal.taie.analysis.pta.pts.PointsToSet;
import pascal.taie.analysis.pta.pts.PointsToSetFactory;
import pascal.taie.config.AnalysisOptions;
import pascal.taie.ir.exp.InvokeExp;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.Copy;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.ir.stmt.LoadArray;
import pascal.taie.ir.stmt.LoadField;
import pascal.taie.ir.stmt.New;
import pascal.taie.ir.stmt.StmtVisitor;
import pascal.taie.ir.stmt.StoreArray;
import pascal.taie.ir.stmt.StoreField;
import pascal.taie.language.classes.JField;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.type.Type;

import java.util.List;

class Solver {

    private static final Logger logger = LogManager.getLogger(Solver.class);

    private final AnalysisOptions options;

    private final HeapModel heapModel;

    private final ContextSelector contextSelector;

    private CSManager csManager;

    private CSCallGraph callGraph;

    private PointerFlowGraph pointerFlowGraph;

    private WorkList workList;

    private PointerAnalysisResult result;

    Solver(AnalysisOptions options, HeapModel heapModel,
           ContextSelector contextSelector) {
        this.options = options;
        this.heapModel = heapModel;
        this.contextSelector = contextSelector;
    }

    void solve() {
        initialize();
        analyze();
    }

    private void initialize() {
        csManager = new MapBasedCSManager();
        callGraph = new CSCallGraph(csManager);
        pointerFlowGraph = new PointerFlowGraph();
        workList = new WorkList();
        // process program entry, i.e., main method
        Context defContext = contextSelector.getEmptyContext();
        JMethod main = World.get().getMainMethod();
        CSMethod csMethod = csManager.getCSMethod(defContext, main);
        callGraph.addEntryMethod(csMethod);
        addReachable(csMethod);
    }

    /**
     * Processes new reachable context-sensitive method.
     */
    private void addReachable(CSMethod csMethod) {
        // TODO - finish me

        if (!callGraph.contains(csMethod)){
            callGraph.addReachableMethod(csMethod);
            csMethod.getMethod().getIR().stmts().forEach(stmt -> {
                stmt.accept(new StmtProcessor(csMethod));
            });
        }

    }

    /**
     * Processes the statements in context-sensitive new reachable methods.
     */
    private class StmtProcessor implements StmtVisitor<Void> {

        private final CSMethod csMethod;

        private final Context context;

        private StmtProcessor(CSMethod csMethod) {
            this.csMethod = csMethod;
            this.context = csMethod.getContext();
        }

        // TODO - if you choose to implement addReachable()
        //  via visitor pattern, then finish me

        @Override
        public Void visit(New stmt) {
            // i : x = new T()
            Obj obj = heapModel.getObj(stmt);
            Context objContext = contextSelector.selectHeapContext(csMethod,obj);
            CSObj csObj = csManager.getCSObj(objContext,obj);
            PointsToSet pointsToSet = PointsToSetFactory.make(csObj);
            Var x = stmt.getLValue();
            CSVar xPointer = csManager.getCSVar(context,x);
            workList.addEntry(xPointer,pointsToSet);
            return null;
        }

        @Override
        public Void visit(Copy stmt) {
            // x = y
            Var x = stmt.getLValue();
            Var y = stmt.getRValue();
            CSVar xPointer = csManager.getCSVar(context,x);
            CSVar yPointer = csManager.getCSVar(context,y);

            addPFGEdge(yPointer,xPointer);

            return null;
        }

        @Override
        public Void visit(LoadField stmt) {
            if (stmt.isStatic()){
                // y = T.f
                Var y = stmt.getLValue();
                CSVar yPointer = csManager.getCSVar(context,y);

                JField field = stmt.getFieldAccess().getFieldRef().resolve();
                StaticField Tf = csManager.getStaticField(field);

                addPFGEdge(Tf,yPointer);
            }

            return null;
        }

        @Override
        public Void visit(StoreField stmt) {
            if (stmt.isStatic()){
                // T.f = y
                Var y = stmt.getRValue();
                CSVar yPointer = csManager.getCSVar(context,y);

                JField field = stmt.getFieldAccess().getFieldRef().resolve();
                StaticField Tf = csManager.getStaticField(field);

                addPFGEdge(yPointer,Tf);

            }
            return null;
        }

        @Override
        public Void visit(Invoke stmt) {
            if (stmt.isStatic()){
                // l: r = T.m(a1,...,an)
                CSCallSite csCallSite = csManager.getCSCallSite(context,stmt);
                JMethod method = resolveCallee(null, stmt);
                Context ct = contextSelector.selectContext(csCallSite,method);
                processCallSub(csCallSite,method,ct);
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

            if (n instanceof CSVar cx){
                delta.forEach(csObj -> {
                    Var xVar = cx.getVar();

                    // x.f = y
                    xVar.getStoreFields().forEach(storeField -> {
                        CSVar y = csManager.getCSVar(cx.getContext(),storeField.getRValue());
                        JField field = storeField.getFieldAccess().getFieldRef().resolve();
                        InstanceField oif = csManager.getInstanceField(csObj,field);
                        addPFGEdge(y,oif);
                    });

                    // y = x.f
                    xVar.getLoadFields().forEach(loadField -> {
                        CSVar y = csManager.getCSVar(cx.getContext(),loadField.getLValue());
                        JField field = loadField.getFieldAccess().getFieldRef().resolve();
                        InstanceField oif = csManager.getInstanceField(csObj,field);
                        addPFGEdge(oif,y);
                    });


                    // x[i] = y
                    xVar.getStoreArrays().forEach(storeArray -> {
                        CSVar y = csManager.getCSVar(cx.getContext(),storeArray.getRValue());
                        ArrayIndex xi = csManager.getArrayIndex(csObj);
                        addPFGEdge(y,xi);
                    });

                    // y = x[i]
                    xVar.getLoadArrays().forEach(loadArray -> {
                        CSVar y = csManager.getCSVar(cx.getContext(),loadArray.getLValue());
                        ArrayIndex xi = csManager.getArrayIndex(csObj);
                        addPFGEdge(xi,y);
                    });

                    processCall(cx,csObj);
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
        PointsToSet delta = PointsToSetFactory.make();
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

    private void processCallSub(CSCallSite csCallSite, JMethod method , Context ct){
        CSMethod csMethod = csManager.getCSMethod(ct,method);
        if (!callGraph.getCalleesOf(csCallSite).contains(csMethod)){
            Invoke stmt = csCallSite.getCallSite();
            callGraph.addEdge(new Edge<>(CallGraphs.getCallKind(stmt),csCallSite,csMethod));

            addReachable(csMethod);

            List<Var> params = method.getIR().getParams();
            List<Var> args = csCallSite.getCallSite().getInvokeExp().getArgs();

            for (int i = 0; i < args.size();i++){
                addPFGEdge(
                        csManager.getCSVar(csCallSite.getContext(),args.get(i)),
                        csManager.getCSVar(ct,params.get(i))
                );
            }

            Var returnVar = stmt.getResult();

            if (returnVar != null){
                CSVar csReturnVar = csManager.getCSVar(csCallSite.getContext(),returnVar);

                List<Var> mReturnVars = method.getIR().getReturnVars();

                mReturnVars.forEach(var -> {
                    addPFGEdge(
                            csManager.getCSVar(ct,var),
                            csReturnVar
                    );
                });
            }
        }
    }


    /**
     * Processes instance calls when points-to set of the receiver variable changes.
     *
     * @param recv    the receiver variable
     * @param recvObj set of new discovered objects pointed by the variable.
     */
    private void processCall(CSVar recv, CSObj recvObj) {
        // TODO - finish me

        recv.getVar().getInvokes().forEach(invoke -> {
            JMethod method = resolveCallee(recvObj,invoke);
            CSCallSite csCallSite = csManager.getCSCallSite(recv.getContext(),invoke);
            Context targetContext = contextSelector.selectContext(csCallSite,recvObj,method);

            Var thisVar = method.getIR().getThis();
            CSVar csThisVar = csManager.getCSVar(targetContext,thisVar);

            workList.addEntry(csThisVar,PointsToSetFactory.make(recvObj));

            processCallSub(csCallSite,method,targetContext);

        });
    }

    /**
     * Resolves the callee of a call site with the receiver object.
     *
     * @param recv the receiver object of the method call. If the callSite
     *             is static, this parameter is ignored (i.e., can be null).
     * @param callSite the call site to be resolved.
     * @return the resolved callee.
     */
    private JMethod resolveCallee(CSObj recv, Invoke callSite) {
        Type type = recv != null ? recv.getObject().getType() : null;
        return CallGraphs.resolveCallee(type, callSite);
    }

    PointerAnalysisResult getResult() {
        if (result == null) {
            result = new PointerAnalysisResultImpl(csManager, callGraph);
        }
        return result;
    }
}

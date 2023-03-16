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

package pascal.taie.analysis.pta.plugin.taint;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pascal.taie.World;
import pascal.taie.analysis.pta.PointerAnalysisResult;
import pascal.taie.analysis.pta.core.cs.context.Context;
import pascal.taie.analysis.pta.core.cs.element.CSCallSite;
import pascal.taie.analysis.pta.core.cs.element.CSManager;
import pascal.taie.analysis.pta.core.cs.element.CSObj;
import pascal.taie.analysis.pta.core.cs.element.CSVar;
import pascal.taie.analysis.pta.core.heap.Obj;
import pascal.taie.analysis.pta.cs.Solver;
import pascal.taie.analysis.pta.pts.PointsToSet;
import pascal.taie.analysis.pta.pts.PointsToSetFactory;
import pascal.taie.ir.exp.InvokeInstanceExp;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.type.Type;
import pascal.taie.util.collection.Maps;
import pascal.taie.util.collection.MultiMap;
import pascal.taie.util.collection.TwoKeyMap;

import javax.annotation.Nullable;
import java.util.*;

public class TaintAnalysiss {

    private static final Logger logger = LogManager.getLogger(TaintAnalysiss.class);

    private final TaintManager manager;

    private final TaintConfig config;

    private final Solver solver;

    private final CSManager csManager;

    private final Context emptyContext;

    private HashMap<JMethod,Source> method2souce = new HashMap<>();
    
    private HashMap<JMethod, HashMap<Integer, Sink>> method2sink = new HashMap<>();

    private HashMap<JMethod,TaintTransfer> base_to_resultTransfers = new HashMap<>();
    private TwoKeyMap<JMethod, Integer, TaintTransfer> arg_to_baseTransfers = Maps.newTwoKeyMap();

    private TwoKeyMap<JMethod, Integer, TaintTransfer> arg_to_resultTransfers = Maps.newTwoKeyMap();

    private MultiMap<Sink,CSCallSite> sink2cscallsites = Maps.newMultiMap();


    public TaintAnalysiss(Solver solver) {
        manager = new TaintManager();
        this.solver = solver;
        csManager = solver.getCSManager();
        emptyContext = solver.getContextSelector().getEmptyContext();
        config = TaintConfig.readConfig(
                solver.getOptions().getString("taint-config"),
                World.get().getClassHierarchy(),
                World.get().getTypeSystem());
        logger.info(config);

        initMethod2Souce();
        initTransfers();
        initMethod2Sinks();
    }

    private void initMethod2Souce(){
        config.getSources().forEach(source -> {
           method2souce.put(source.method(),source);
        });
    }

    private void initMethod2Sinks(){
        method2sink = new HashMap<>();
        config.getSinks().forEach(sink -> {
            JMethod method = sink.method();
            if (!method2sink.containsKey(method)){
                HashMap m = new HashMap();
                m.put(sink.index(),sink);
                method2sink.put(method,m);
            }
        });
    }

    private void initTransfers(){
        config.getTransfers().forEach(taintTransfer -> {
            if (taintTransfer.from() == TaintTransfer.BASE && taintTransfer.to() == TaintTransfer.RESULT){
                base_to_resultTransfers.put(taintTransfer.method(),taintTransfer);
            }else if (taintTransfer.from() >= 0 && taintTransfer.to() == TaintTransfer.BASE){
                JMethod method = taintTransfer.method();
                if (!arg_to_baseTransfers.containsKey(method)){
                    arg_to_baseTransfers.put(method,taintTransfer.from(),taintTransfer);
                }
            }else if (taintTransfer.from() >= 0 && taintTransfer.to() == TaintTransfer.RESULT){
                JMethod method = taintTransfer.method();
                if (!arg_to_resultTransfers.containsKey(method)){
                    arg_to_resultTransfers.put(method,taintTransfer.from(),taintTransfer);
                }
            }
        });
    }

    public void processSink(CSCallSite csCallSite , JMethod method){
        if (method2sink.containsKey(method)){
            method2sink.get(method).entrySet().forEach(integerSinkEntry -> {
                Sink sink = integerSinkEntry.getValue();
                sink2cscallsites.put(sink,csCallSite);
            });
        }
    }

    // TODO - finish me

    public void onFinish() {
        Set<TaintFlow> taintFlows = collectTaintFlows();
        solver.getResult().storeResult(getClass().getName(), taintFlows);
    }

    private Set<TaintFlow> collectTaintFlows() {
        Set<TaintFlow> taintFlows = new TreeSet<>();
        PointerAnalysisResult result = solver.getResult();
        // TODO - finish me
        // You could query pointer analysis results you need via variable result.

        sink2cscallsites.forEachSet((sink, csCallSites) -> {
            csCallSites.forEach(csCallSite -> {
                List<Var> args = csCallSite.getCallSite().getRValue().getArgs();
                Set<Obj> pointsToSet = result.getPointsToSet(args.get(sink.index()));

                for (Obj obj : pointsToSet){
                    if (manager.isTaint(obj)){
                        taintFlows.add(
                                new TaintFlow(
                                        manager.getSourceCall(obj),
                                        csCallSite.getCallSite(),
                                        sink.index()
                                )
                        );
                    }
                }
            });
        });

        return taintFlows;
    }

    public boolean isSouceMethod(JMethod jMethod){
        return method2souce.containsKey(jMethod);
    }
    public void processSource(CSCallSite csCallSite , JMethod method){
        Invoke callsite = csCallSite.getCallSite();
        Var returnVar = callsite.getResult();

        if (returnVar != null) {
            CSVar csReturnVar = csManager.getCSVar(csCallSite.getContext(), returnVar);
            Obj taintObj = manager.makeTaint(callsite , method2souce.get(method).type());
            CSObj csTaintObj = csManager.getCSObj(emptyContext,taintObj);
            solver.workListAddEntry(csReturnVar, PointsToSetFactory.make(csTaintObj));
        }
    }

    public boolean isBase_to_resultTransferMethod(JMethod method){
        return base_to_resultTransfers.containsKey(method);
    }

    public void processBase_to_resultTransfer(CSObj csTaintObj , CSCallSite csCallSite , JMethod method){
        if (csTaintObj != null){
            if (manager.isTaint(csTaintObj.getObject())){
                Invoke callsite = csCallSite.getCallSite();
                Var returnVar = callsite.getResult();

                if (returnVar != null) {
                    TaintTransfer taintTransfer = base_to_resultTransfers.get(method);

                    CSVar csReturnVar = csManager.getCSVar(csCallSite.getContext(), returnVar);

                    Invoke sourceInvoke = manager.getSourceCall(csTaintObj.getObject());
                    Obj newTaintObj = manager.makeTaint(sourceInvoke,taintTransfer.type());

                    CSObj newCSTaintObj = csManager.getCSObj(emptyContext,newTaintObj);

                    solver.workListAddEntry(csReturnVar,PointsToSetFactory.make(newCSTaintObj));
                }
            }
        }
    }

    public boolean isArg_to_baseTransferMethod(JMethod method , Integer index){
        return arg_to_baseTransfers.containsKey(method) && arg_to_baseTransfers.containsKey(method,index);
    }

    public void processArg_to_baseTransfer(CSVar base, CSCallSite csCallSite, JMethod method , int i){
        List<Var> args = csCallSite.getCallSite().getRValue().getArgs();
        Context c = csCallSite.getContext();

        Var arg = args.get(i);
        CSVar csArg = csManager.getCSVar(c,arg);
        PointsToSet pts = csArg.getPointsToSet();

        pts.getObjects().forEach(csObj -> {
            if (manager.isTaint(csObj.getObject())){
                Obj taintObj = manager.makeTaint(manager.getSourceCall(csObj.getObject()),arg_to_baseTransfers.get(method,i).type());
                CSObj csTaintObj = csManager.getCSObj(emptyContext,taintObj);
                solver.workListAddEntry(base,PointsToSetFactory.make(csTaintObj));

            }
        });

    }

    public boolean isArg_to_resultTransferMethod(JMethod method,Integer index){
        return arg_to_resultTransfers.containsKey(method) && arg_to_resultTransfers.containsKey(method,index);
    }


    public void processArg_to_resultTransfer(CSCallSite csCallSite , JMethod method , int i){
        List<Var> args = csCallSite.getCallSite().getRValue().getArgs();
        Var arg = args.get(i);

        Invoke callsite = csCallSite.getCallSite();
        Var returnVar = callsite.getResult();

        if (returnVar != null) {

            CSVar csReturnVar = csManager.getCSVar(csCallSite.getContext(), returnVar);

            CSVar csArg = csManager.getCSVar(csCallSite.getContext(),arg);

            PointsToSet pts = csArg.getPointsToSet();


            pts.getObjects().forEach(csObj -> {
                if (manager.isTaint(csObj.getObject())){
                    Obj taintObj = manager.makeTaint(manager.getSourceCall(csObj.getObject()),arg_to_resultTransfers.get(method,i).type());
                    CSObj csTaintObj = csManager.getCSObj(emptyContext,taintObj);
                    solver.workListAddEntry(csReturnVar,PointsToSetFactory.make(csTaintObj));
                }
            });
        }

    }

    public void processTaintInvoke(CSVar base, CSObj baseObj,CSCallSite csCallSite, JMethod method){
        if (isSouceMethod(method)) {
            processSource(csCallSite, method);
        }else if (isBase_to_resultTransferMethod(method)){
            processBase_to_resultTransfer(baseObj,csCallSite,method);
        }
        List<Var> args = csCallSite.getCallSite().getRValue().getArgs();
        for (int i = 0; i < args.size(); i++) {
            if (isArg_to_resultTransferMethod(method, i)) {
                processArg_to_resultTransfer(csCallSite,method,i);
            }
            if (isArg_to_baseTransferMethod(method,i)){
                processArg_to_baseTransfer(base,csCallSite,method,i);
            }
        }
    }


}




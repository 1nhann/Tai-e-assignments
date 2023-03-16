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

package pascal.taie.analysis.dataflow.inter;

import pascal.taie.World;
import pascal.taie.analysis.dataflow.analysis.constprop.CPFact;
import pascal.taie.analysis.dataflow.analysis.constprop.ConstantPropagation;
import pascal.taie.analysis.dataflow.analysis.constprop.Value;
import pascal.taie.analysis.graph.cfg.CFG;
import pascal.taie.analysis.graph.cfg.CFGBuilder;
import pascal.taie.analysis.graph.icfg.CallEdge;
import pascal.taie.analysis.graph.icfg.CallToReturnEdge;
import pascal.taie.analysis.graph.icfg.NormalEdge;
import pascal.taie.analysis.graph.icfg.ReturnEdge;
import pascal.taie.analysis.pta.PointerAnalysisResult;
import pascal.taie.analysis.pta.core.cs.element.CSObj;
import pascal.taie.analysis.pta.core.cs.element.CSVar;
import pascal.taie.analysis.pta.core.cs.element.InstanceField;
import pascal.taie.analysis.pta.core.heap.Obj;
import pascal.taie.config.AnalysisConfig;
import pascal.taie.ir.IR;
import pascal.taie.ir.exp.FieldAccess;
import pascal.taie.ir.exp.InstanceFieldAccess;
import pascal.taie.ir.exp.InvokeExp;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.*;
import pascal.taie.language.classes.JField;
import pascal.taie.language.classes.JMethod;
import pascal.taie.util.collection.Maps;
import pascal.taie.util.collection.MultiMap;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Implementation of interprocedural constant propagation for int values.
 */
public class InterConstantPropagation extends
        AbstractInterDataflowAnalysis<JMethod, Stmt, CPFact> {

    public static final String ID = "inter-constprop";

    private final ConstantPropagation cp;

    private MultiMap<Var,Var> alias;

    public InterConstantPropagation(AnalysisConfig config) {
        super(config);
        cp = new ConstantPropagation(new AnalysisConfig(ConstantPropagation.ID));
    }

    @Override
    protected void initialize() {
        String ptaId = getOptions().getString("pta");
        PointerAnalysisResult pta = World.get().getResult(ptaId);
        // You can do initialization work here
        this.alias = initializeAlias(pta);
    }

    private MultiMap<Var, Var> initializeAlias(PointerAnalysisResult pta){

        MultiMap<Obj, Var> obj2vars = Maps.newMultiMap();
        MultiMap<Var, Var> alias = Maps.newMultiMap();

        pta.getVars().forEach(var -> {
            pta.getPointsToSet(var).forEach(obj -> {
                obj2vars.put(obj,var);
            });
        });

        obj2vars.forEachSet((obj, vars) -> {
            vars.forEach(var -> {
                alias.putAll(var,vars);
            });
        });

        return alias;
    }

    @Override
    public boolean isForward() {
        return cp.isForward();
    }

    @Override
    public CPFact newBoundaryFact(Stmt boundary) {
        IR ir = icfg.getContainingMethodOf(boundary).getIR();
        return cp.newBoundaryFact(ir.getResult(CFGBuilder.ID));
    }

    @Override
    public CPFact newInitialFact() {
        return cp.newInitialFact();
    }

    @Override
    public void meetInto(CPFact fact, CPFact target) {
        cp.meetInto(fact, target);
    }

    @Override
    protected boolean transferCallNode(Stmt stmt, CPFact in, CPFact out) {
        // TODO - finish me
//        return false;
        return out.copyFrom(in);
    }

    private Set<StoreField> getStaticStoreFieldStmts(JField field){
        return getStoreFieldStmts(null,field);
    }

    private Set<StoreField> getStoreFieldStmts(Var baseVar,JField field){
        HashSet<StoreField> stmts = new HashSet<>();
        if (baseVar == null){
            icfg.forEach(stmt1 -> {
                if (stmt1 instanceof StoreField storeField &&
                        storeField.isStatic() &&
                        storeField.getFieldAccess().getFieldRef().resolve().equals(field)){
                    stmts.add(storeField);
                }
            });
        }else {
            Set<Var> aliases = alias.get(baseVar);
            aliases.forEach(var -> {
                List<StoreField> storeFields = var.getStoreFields();
                storeFields.forEach(storeField -> {
                    if (storeField.getFieldAccess().getFieldRef().resolve().equals(field)){
                        stmts.add(storeField);
                    }
                });
            });
        }
        return stmts;
    }

    private Set<LoadField> getStaticLoadFieldStmts(JField field){
        return getLoadFieldStmts(null,field);
    }

    private Set<LoadField> getLoadFieldStmts(Var baseVar,JField field){
        HashSet<LoadField> stmts = new HashSet<>();
        if (baseVar == null){
            icfg.forEach(stmt1 -> {
                if (stmt1 instanceof LoadField loadField &&
                        loadField.isStatic() &&
                        loadField.getFieldAccess().getFieldRef().resolve().equals(field)){
                    stmts.add(loadField);
                }
            });
        }else {
            Set<Var> aliases = alias.get(baseVar);
            aliases.forEach(var -> {
                List<LoadField> loadFields = var.getLoadFields();
                loadFields.forEach(loadField -> {
                    if (loadField.getFieldAccess().getFieldRef().resolve().equals(field)){
                        stmts.add(loadField);
                    }
                });
            });
        }
        return stmts;
    }


    private Value mergeAssignStmtValue(Set<? extends AssignStmt> stmts){
        Value value = Value.getUndef();

        for (AssignStmt stmt : stmts){
            if (stmt.getRValue() instanceof Var x){
                // y = x
                Value v = solver.getInFact(stmt).get(x);
                value = cp.meetValue(v,value);
            }
        }
        return value;
    }


    private Set<StoreArray> getStoreArrayStmts(Var baseVar,Value iValue){
        Set<Var> aliases = alias.get(baseVar);
        Set<StoreArray> stmts = new HashSet<>();

        aliases.forEach(var -> {
            List<StoreArray> storeArrays = var.getStoreArrays();
            storeArrays.forEach(storeArray -> {
                Var j = storeArray.getArrayAccess().getIndex();

                Value jValue = solver.getInFact(storeArray).get(j);

                if (
                        (iValue.isConstant() && jValue.isNAC())
                        || (iValue.isNAC() && jValue.isConstant())
                        || (iValue.isNAC() && jValue.isNAC())
                        || (
                                iValue.isConstant()
                                        && jValue.isConstant()
                                        && iValue.getConstant() == jValue.getConstant()
                                )
                ){
                    stmts.add(storeArray);
                }
            });
        });
        return stmts;
    }


    private Set<LoadArray> getLoadArrayStmts(Var baseVar,Var index){
        Set<Var> aliases = alias.get(baseVar);
        Set<LoadArray> stmts = new HashSet<>();

        aliases.forEach(var -> {
            List<LoadArray> loadArrays = var.getLoadArrays();
            loadArrays.forEach(loadArray -> {
                Var i = loadArray.getArrayAccess().getIndex();
                if (solver.getInFact(loadArray).get(i).equals(solver.getInFact(loadArray).get(index))){
                    stmts.add(loadArray);
                }
            });
        });
        return stmts;
    }

    private boolean transferLoadField(LoadField loadField, CPFact in, CPFact out){
        CPFact old_OUT = out.copy();
        out.copyFrom(in);
        JField field = loadField.getFieldAccess().getFieldRef().resolve();
        if (loadField.isStatic()){
            // y = T.f
            Set<StoreField> stmts = getStaticStoreFieldStmts(field);
            Value value = mergeAssignStmtValue(stmts);
            Var y = loadField.getLValue();
            value = cp.meetValue(in.get(y),value);
            out.update(y,value);
        }else {
            // y = x.f
            FieldAccess fieldAccess = loadField.getFieldAccess();
            if (fieldAccess instanceof InstanceFieldAccess instanceFieldAccess){
                Var baseVar = instanceFieldAccess.getBase();
                Set<StoreField> stmts = getStoreFieldStmts(baseVar,field);
                Value value = mergeAssignStmtValue(stmts);
                Var y = loadField.getLValue();
                value = cp.meetValue(in.get(y),value);
                out.update(y,value);
            }
        }
        return !old_OUT.equals(out);
    }

    private boolean transferLoadArray(LoadArray loadArray, CPFact in, CPFact out){
        CPFact old_OUT = out.copy();
        out.copyFrom(in);
        // y = x[i]
        Var x = loadArray.getArrayAccess().getBase();
        Var i = loadArray.getArrayAccess().getIndex();
        Value iValue = solver.getInFact(loadArray).get(i);
        Set<StoreArray> storeArrays = getStoreArrayStmts(x,iValue);

        Value value = mergeAssignStmtValue(storeArrays);

        Var y = loadArray.getLValue();
        value = cp.meetValue(in.get(y),value);
        out.update(y,value);
        return !old_OUT.equals(out);
    }


    private boolean transferStoreField(StoreField storeField, CPFact in, CPFact out){
        CPFact old_OUT = out.copy();
        out.copyFrom(in);
        JField field = storeField.getFieldAccess().getFieldRef().resolve();
        Set<LoadField> stmts = new HashSet<>();
        if (storeField.isStatic()){
            // T.f = y
            stmts = getStaticLoadFieldStmts(field);
        }else {
            // x.f = y
            FieldAccess fieldAccess = storeField.getFieldAccess();
            if (fieldAccess instanceof InstanceFieldAccess instanceFieldAccess){
                Var baseVar = instanceFieldAccess.getBase();
                stmts = getLoadFieldStmts(baseVar,field);
            }
        }
        solver.getWorkList().addAll(stmts);
        return !old_OUT.equals(out);
    }

    private boolean transferStoreArray(StoreArray storeArray, CPFact in, CPFact out){
        CPFact old_OUT = out.copy();
        out.copyFrom(in);
        // y = x[i]
        Var x = storeArray.getArrayAccess().getBase();
        Var i = storeArray.getArrayAccess().getIndex();
        Set<LoadArray> stmts = getLoadArrayStmts(x,i);

        solver.getWorkList().addAll(stmts);
        return !old_OUT.equals(out);
    }


    @Override
    protected boolean transferNonCallNode(Stmt stmt, CPFact in, CPFact out) {
        // TODO - finish me
//        return false;

        if (stmt instanceof LoadField loadField){
            return transferLoadField(loadField,in,out);
        }else if (stmt instanceof LoadArray loadArray) {
            return transferLoadArray(loadArray,in,out);
        } else if (stmt instanceof StoreField storeField) {
            return transferStoreField(storeField,in,out);
        } else if (stmt instanceof StoreArray storeArray) {
            return transferStoreArray(storeArray,in,out);
        }
        return this.cp.transferNode(stmt,in,out);
    }

    @Override
    protected CPFact transferNormalEdge(NormalEdge<Stmt> edge, CPFact out) {
        // TODO - finish me
//        return null;
        return out.copy();
    }

    @Override
    protected CPFact transferCallToReturnEdge(CallToReturnEdge<Stmt> edge, CPFact out) {
        // TODO - finish me
//        return null;

        Stmt stmt = edge.getSource();

        CPFact fact = out.copy();

        if (stmt instanceof Invoke callsite){
            Var lvar = callsite.getResult();

            if (lvar != null){
                fact.remove(lvar);
            }
        }

        return fact;
    }

    @Override
    protected CPFact transferCallEdge(CallEdge<Stmt> edge, CPFact callSiteOut) {
        // TODO - finish me
//        return null;

        Stmt source = edge.getSource();

        CPFact fact = new CPFact();

        if (source instanceof Invoke callsite){
            List<Var> vars = callsite.getInvokeExp().getArgs();
            JMethod callee = edge.getCallee();
            List<Var> params = callee.getIR().getParams();

            for (int i = 0; i < vars.size();i++){
                fact.update(params.get(i),callSiteOut.get(vars.get(i)));
            }
        }

        return fact;

    }

    @Override
    protected CPFact transferReturnEdge(ReturnEdge<Stmt> edge, CPFact returnOut) {
        // TODO - finish me
//        return null;

        Stmt stmt = edge.getCallSite();

        CPFact fact = new CPFact();

        if (stmt instanceof Invoke callsite){
            Var resultVar = callsite.getResult();

            if (resultVar != null){

                Value returnValue = Value.getUndef();

                for (Var returnVar : edge.getReturnVars()){
                    returnValue = this.cp.meetValue(returnValue,returnOut.get(returnVar));
                }

                fact.update(resultVar,returnValue);
            }
        }

        return fact;

    }
}

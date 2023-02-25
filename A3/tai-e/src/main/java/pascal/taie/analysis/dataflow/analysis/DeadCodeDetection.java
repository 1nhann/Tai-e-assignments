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

package pascal.taie.analysis.dataflow.analysis;

import pascal.taie.analysis.MethodAnalysis;
import pascal.taie.analysis.dataflow.analysis.constprop.CPFact;
import pascal.taie.analysis.dataflow.analysis.constprop.ConstantPropagation;
import pascal.taie.analysis.dataflow.analysis.constprop.Value;
import pascal.taie.analysis.dataflow.fact.DataflowResult;
import pascal.taie.analysis.dataflow.fact.SetFact;
import pascal.taie.analysis.graph.cfg.CFG;
import pascal.taie.analysis.graph.cfg.CFGBuilder;
import pascal.taie.analysis.graph.cfg.Edge;
import pascal.taie.config.AnalysisConfig;
import pascal.taie.ir.IR;
import pascal.taie.ir.exp.*;
import pascal.taie.ir.stmt.AssignStmt;
import pascal.taie.ir.stmt.If;
import pascal.taie.ir.stmt.Stmt;
import pascal.taie.ir.stmt.SwitchStmt;

import java.util.*;

public class DeadCodeDetection extends MethodAnalysis {

    public static final String ID = "deadcode";

    public DeadCodeDetection(AnalysisConfig config) {
        super(config);
    }

    @Override
    public Set<Stmt> analyze(IR ir) {
        // obtain CFG
        CFG<Stmt> cfg = ir.getResult(CFGBuilder.ID);
        // obtain result of constant propagation
        DataflowResult<Stmt, CPFact> constants =
                ir.getResult(ConstantPropagation.ID);
        // obtain result of live variable analysis
        DataflowResult<Stmt, SetFact<Var>> liveVars =
                ir.getResult(LiveVariableAnalysis.ID);
        // keep statements (dead code) sorted in the resulting set
        Set<Stmt> deadCode = new TreeSet<>(Comparator.comparing(Stmt::getIndex));
        // TODO - finish me
        // Your task is to recognize dead code in ir and add it to deadCode

        cfg.forEach(deadCode::add);
        deadCode.remove(cfg.getExit());
        deadCode.remove(cfg.getEntry());

        Set<Stmt> reachableCode = getReachableCode(cfg,constants);

        deadCode.removeAll(reachableCode);

        Set<Stmt> deadAssignStmt = getDeadAssignStmt(cfg,liveVars);

        deadCode.addAll(deadAssignStmt);

        return deadCode;
    }

    private Set<Stmt> getReachableCode(CFG<Stmt> cfg , DataflowResult<Stmt, CPFact> constants){
        Set<Stmt> reachableCode = new HashSet<>();

        Queue<Stmt> queue = new LinkedList<>();
        queue.add(cfg.getEntry());

        //bfs
        while (!queue.isEmpty()){
            Stmt stmt = queue.poll();

            if (reachableCode.contains(stmt)){
                continue;
            }else {
                reachableCode.add(stmt);
            }

            Set<Stmt> succs = cfg.getSuccsOf(stmt);
            if (succs.size() == 1){
                queue.addAll(succs);
            }else if(succs.size() > 1){
                if (stmt instanceof If){
                    Set<Edge<Stmt>> outEdges = cfg.getOutEdgesOf(stmt);
                    ConditionExp conditionExp = ((If) stmt).getCondition();

                    Value conditionValue = ConstantPropagation.evaluate(conditionExp,constants.getInFact(stmt));

                    if (conditionValue.isConstant()){
                        boolean conditionValueBool = conditionValue.getConstant() == 1;
                        outEdges.forEach(stmtEdge -> {
                            if (stmtEdge.getKind().equals(Edge.Kind.IF_TRUE) && conditionValueBool){
                                queue.add(stmtEdge.getTarget());
                            }else if (stmtEdge.getKind().equals(Edge.Kind.IF_FALSE) && !conditionValueBool){
                                queue.add(stmtEdge.getTarget());
                            }
                        });
                    }else {
                        queue.addAll(succs);
                    }
                }

                if (stmt instanceof SwitchStmt){
                    Set<Edge<Stmt>> outEdges = cfg.getOutEdgesOf(stmt);

                    Var conditionVar = ((SwitchStmt) stmt).getVar();

                    Value conditionValue = constants.getInFact(stmt).get(conditionVar);

                    if (conditionValue.isConstant()){
                        int value = conditionValue.getConstant();
                        boolean useDefault = true;
                        Stmt defaultTarget = null;
                        for (Edge<Stmt> edge:outEdges){
                            if (edge.getKind().equals(Edge.Kind.SWITCH_CASE)){
                                if (edge.getCaseValue() == value){
                                    queue.add(edge.getTarget());
                                    useDefault = false;
                                    break;
                                }
                            }else if (edge.getKind().equals(Edge.Kind.SWITCH_DEFAULT)){
                                defaultTarget = edge.getTarget();
                            }
                        }
                        if (useDefault && defaultTarget != null){
                            queue.add(defaultTarget);
                        }
                    }else {
                        queue.addAll(succs);
                    }

                }

            }

        }
        return reachableCode;
    }

    private Set<Stmt> getDeadAssignStmt(CFG<Stmt> cfg, DataflowResult<Stmt, SetFact<Var>> liveVars){
        Set<Stmt> deadAssignStmt = new HashSet<>();

        for (Stmt stmt: cfg){
            if (stmt instanceof AssignStmt){
                LValue lValue = ((AssignStmt) stmt).getLValue();
                SetFact<Var> fact = liveVars.getOutFact(stmt);
                if (lValue instanceof Var && !fact.contains((Var) lValue)){
                    if (DeadCodeDetection.hasNoSideEffect(((AssignStmt) stmt).getRValue())){
                        deadAssignStmt.add(stmt);
                    }
                }
            }
        }
        return deadAssignStmt;
    }


    /**
     * @return true if given RValue has no side effect, otherwise false.
     */
    private static boolean hasNoSideEffect(RValue rvalue) {
        // new expression modifies the heap
        if (rvalue instanceof NewExp ||
                // cast may trigger ClassCastException
                rvalue instanceof CastExp ||
                // static field access may trigger class initialization
                // instance field access may trigger NPE
                rvalue instanceof FieldAccess ||
                // array access may trigger NPE
                rvalue instanceof ArrayAccess) {
            return false;
        }
        if (rvalue instanceof ArithmeticExp) {
            ArithmeticExp.Op op = ((ArithmeticExp) rvalue).getOperator();
            // may trigger DivideByZeroException
            return op != ArithmeticExp.Op.DIV && op != ArithmeticExp.Op.REM;
        }
        return true;
    }
}

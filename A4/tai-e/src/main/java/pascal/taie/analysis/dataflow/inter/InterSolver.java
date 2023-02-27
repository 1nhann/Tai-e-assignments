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

import pascal.taie.analysis.dataflow.fact.DataflowResult;
import pascal.taie.analysis.graph.icfg.ICFG;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * Solver for inter-procedural data-flow analysis.
 * The workload of inter-procedural analysis is heavy, thus we always
 * adopt work-list algorithm for efficiency.
 */
class InterSolver<Method, Node, Fact> {

    private final InterDataflowAnalysis<Node, Fact> analysis;

    private final ICFG<Method, Node> icfg;

    private DataflowResult<Node, Fact> result;

    private Queue<Node> workList;

    InterSolver(InterDataflowAnalysis<Node, Fact> analysis,
                ICFG<Method, Node> icfg) {
        this.analysis = analysis;
        this.icfg = icfg;
    }

    DataflowResult<Node, Fact> solve() {
        result = new DataflowResult<>();
        initialize();
        doSolve();
        return result;
    }

    private void initialize() {
        // TODO - finish me
        Node entry = getEntry();
        if (entry != null){
            result.setOutFact(entry,analysis.newBoundaryFact(entry));
            for (Node node:icfg){
                if (!node.equals(entry)){
                    result.setOutFact(node,analysis.newInitialFact());
                    result.setInFact(node,analysis.newInitialFact());
                }
            }
        }
    }


    private Node getEntry(){
        List<Method> methods = icfg.entryMethods().toList();
        if (methods.size() >= 1){
            Method mainMethod = methods.get(0);
            return icfg.getEntryOf(mainMethod);
        }
        return null;
    }

    private void doSolve() {
        // TODO - finish me

        workList = new LinkedList<>();

        icfg.forEach(workList::add);
        workList.remove(getEntry());

//        Node entry = getEntry();
//        for (Node node:icfg){
//            if (!node.equals(entry)){
//                workList.add(node);
//            }
//        }

        while (!workList.isEmpty()){
            Node stmt = workList.poll();

            Fact in = result.getInFact(stmt);

            icfg.getInEdgesOf(stmt).forEach(edge -> {
                Node pre = edge.getSource();
                Fact newOut = analysis.transferEdge(edge,result.getOutFact(pre));
                analysis.meetInto(newOut,in);
            });


            Fact out = result.getOutFact(stmt);

            boolean out_changed = analysis.transferNode(stmt,in,out);

            if (out_changed){
                workList.addAll(icfg.getSuccsOf(stmt));
            }
        }

    }
}











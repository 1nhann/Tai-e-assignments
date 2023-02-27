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

package pascal.taie.analysis.graph.callgraph;

import pascal.taie.World;
import pascal.taie.ir.proginfo.MethodRef;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.language.classes.ClassHierarchy;
import pascal.taie.language.classes.JClass;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.classes.Subsignature;

import java.util.*;

/**
 * Implementation of the CHA algorithm.
 */
class CHABuilder implements CGBuilder<Invoke, JMethod> {
    private ClassHierarchy hierarchy;

    @Override
    public CallGraph<Invoke, JMethod> build() {
        hierarchy = World.get().getClassHierarchy();
        return buildCallGraph(World.get().getMainMethod());
    }

    private CallGraph<Invoke, JMethod> buildCallGraph(JMethod entry) {
        DefaultCallGraph callGraph = new DefaultCallGraph();
        callGraph.addEntryMethod(entry);
        // TODO - finish me

        Queue<JMethod> workList = new LinkedList<>();
        workList.add(entry);

        while (!workList.isEmpty()){
            JMethod method = workList.poll();
            if (!callGraph.contains(method)){
                callGraph.addReachableMethod(method);

                callGraph.callSitesIn(method).forEach(invoke -> {
                    Set<JMethod> T = resolve(invoke);
                    T.forEach(targetMethod -> {
                        Edge<Invoke,JMethod> edge = new Edge<>(CallGraphs.getCallKind(invoke),invoke,targetMethod);
                        callGraph.addEdge(edge);
                        workList.add(targetMethod);
                    });
                });
            }
        }

        return callGraph;
    }

    /**
     * Resolves call targets (callees) of a call site via CHA.
     */
    private Set<JMethod> resolve(Invoke callSite) {
        // TODO - finish me
//        return null;
        Set<JMethod> T = new HashSet<>();
        MethodRef methodRef = callSite.getMethodRef();

        JClass declaringClass = methodRef.getDeclaringClass();
        Subsignature subsignature = methodRef.getSubsignature();

        if (callSite.isStatic()){
            T.add(declaringClass.getDeclaredMethod(subsignature));
        }else if (callSite.isSpecial()){
            T.add(dispatch(declaringClass,subsignature));
        }else if (callSite.isVirtual() || callSite.isInterface()){

            getSubClasses(declaringClass).forEach(subClass -> {
                JMethod method = dispatch(subClass,subsignature);
                if (method != null){
                    T.add(method);
                }
            });

        }
        return T;
    }

    /**
     * Looks up the target method based on given class and method subsignature.
     *
     * @return the dispatched target method, or null if no satisfying method
     * can be found.
     */
    private JMethod dispatch(JClass jclass, Subsignature subsignature) {
        // TODO - finish me
//        return null;
        JMethod method = jclass.getDeclaredMethod(subsignature);
        if (method != null && !method.isAbstract()){
            return method;
        }else {
            JClass superClass = jclass.getSuperClass();
            if (superClass == null){
                return null;
            }
            return dispatch(superClass,subsignature);
        }
    }



    private Set<JClass> getSubClasses(JClass jClass){
        Set<JClass> set = new HashSet<>();
        if (jClass.isInterface()){
            this.hierarchy.getDirectImplementorsOf(jClass).forEach(jClass1 -> {
                set.addAll(getSubClasses(jClass1));
            });

            this.hierarchy.getDirectSubinterfacesOf(jClass).forEach(jClass1 -> {
                set.addAll(getSubClasses(jClass1));
            });
        }else {
            set.add(jClass);
            this.hierarchy.getDirectSubclassesOf(jClass).forEach(jClass1 -> {
                set.addAll(getSubClasses(jClass1));
            });
        }
        return set;
    }
}

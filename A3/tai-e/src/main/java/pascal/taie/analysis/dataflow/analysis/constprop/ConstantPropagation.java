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

package pascal.taie.analysis.dataflow.analysis.constprop;

import pascal.taie.analysis.dataflow.analysis.AbstractDataflowAnalysis;
import pascal.taie.analysis.graph.cfg.CFG;
import pascal.taie.config.AnalysisConfig;
import pascal.taie.ir.exp.ArithmeticExp;
import pascal.taie.ir.exp.BinaryExp;
import pascal.taie.ir.exp.BitwiseExp;
import pascal.taie.ir.exp.ConditionExp;
import pascal.taie.ir.exp.Exp;
import pascal.taie.ir.exp.IntLiteral;
import pascal.taie.ir.exp.ShiftExp;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.DefinitionStmt;
import pascal.taie.ir.stmt.Stmt;
import pascal.taie.language.type.PrimitiveType;
import pascal.taie.language.type.Type;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class ConstantPropagation extends
        AbstractDataflowAnalysis<Stmt, CPFact> {

    public static final String ID = "constprop";

    public ConstantPropagation(AnalysisConfig config) {
        super(config);
    }

    @Override
    public boolean isForward() {
        return true;
    }

    @Override
    public CPFact newBoundaryFact(CFG<Stmt> cfg) {
        // TODO - finish me
//        return null;

        CPFact cpFact = new CPFact();
        List<Var> params = cfg.getIR().getParams();

        params.forEach(var -> {
            if (ConstantPropagation.canHoldInt(var)){
                cpFact.update(var,Value.getNAC());
            }
        });

        return cpFact;
    }

    @Override
    public CPFact newInitialFact() {
        // TODO - finish me
//        return null;
        return new CPFact();
    }

    @Override
    public void meetInto(CPFact fact, CPFact target) {
        // TODO - finish me

        fact.forEach((var, value) -> {
            Value v = meetValue(target.get(var),value);
            target.update(var,v);
        });
    }

    /**
     * Meets two Values.
     */
    public Value meetValue(Value v1, Value v2) {
        // TODO - finish me
//        return null;
        if (v1.isConstant() && v2.isConstant()){
            if (v1.equals(v2)){
                return v1;
            }else {
                return Value.getNAC();
            }
        }

        if (v1.isConstant() && v2.isUndef()){
            return v1;
        }

        if (v1.isUndef() && v2.isConstant()){
            return v2;
        }

        if (v1.isNAC() || v2.isNAC()){
            return Value.getNAC();
        }

        if (v1.isUndef() && v2.isUndef()){
            return Value.getUndef();
        }

        throw new RuntimeException(String.format("Unexpected value of %s and %s",v1,v2));
    }

    @Override
    public boolean transferNode(Stmt stmt, CPFact in, CPFact out) {
        // TODO - finish me
        CPFact old_OUT = out.copy();
        out.copyFrom(in);

        if (stmt instanceof DefinitionStmt){
            Exp lvalue = ((DefinitionStmt)stmt).getLValue();

            if (!(lvalue instanceof Var) || !(ConstantPropagation.canHoldInt((Var) lvalue))){
                return !old_OUT.equals(out);
            }

            out.remove((Var) lvalue);

            CPFact gen = new CPFact();

            Exp rvalue = ((DefinitionStmt)stmt).getRValue();

            if (rvalue instanceof IntLiteral){
                gen.update((Var) lvalue,Value.makeConstant(((IntLiteral) rvalue).getValue()));
            }else if (rvalue instanceof Var){
                gen.update((Var) lvalue,in.get((Var) rvalue));
            }else if (rvalue instanceof BinaryExp){
                gen.update((Var) lvalue,ConstantPropagation.evaluate(rvalue,in));
            }else {
                gen.update((Var) lvalue,Value.getNAC());
            }
            gen.forEach(out::update);

        }
        return !old_OUT.equals(out);
    }

    /**
     * @return true if the given variable can hold integer value, otherwise false.
     */
    public static boolean canHoldInt(Var var) {
        Type type = var.getType();
        if (type instanceof PrimitiveType) {
            switch ((PrimitiveType) type) {
                case BYTE:
                case SHORT:
                case INT:
                case CHAR:
                case BOOLEAN:
                    return true;
            }
        }
        return false;
    }

    /**
     * Evaluates the {@link Value} of given expression.
     *
     * @param exp the expression to be evaluated
     * @param in  IN fact of the statement
     * @return the resulting {@link Value}
     */
    public static Value evaluate(Exp exp, CPFact in) {
        // TODO - finish me
//        return null;
        if (exp instanceof BinaryExp){
            Var y = ((BinaryExp) exp).getOperand1();
            Var z = ((BinaryExp) exp).getOperand2();

            Value yValue = in.get(y);
            Value zValue = in.get(z);

            if (yValue.isConstant() && zValue.isConstant()){
                BinaryExp.Op op = ((BinaryExp) exp).getOperator();
                return evaluateBinaryExp(yValue,op,zValue);

            }else if (yValue.isNAC() || zValue.isNAC()){
                return Value.getNAC();
            }else {
                return Value.getUndef();
            }
        }else {
            return null;
        }
    }

    private static Value evaluateBinaryExp(Value y , BinaryExp.Op op , Value z){
        Integer y1 = y.getConstant();
        Integer z1 = z.getConstant();

        Object result = 0;

        if (op instanceof ArithmeticExp.Op){
            switch ((ArithmeticExp.Op) op){
                case ADD: result = y1 + z1;break;
                case SUB: result = y1 - z1;break;
                case MUL: result = y1 * z1;break;
                case DIV: result = y1 / z1;break;
                case REM: result = y1 % z1;break;
            }
        }
        if (op instanceof BitwiseExp.Op){
            switch ((BitwiseExp.Op) op){
                case OR: result = y1 | z1;break;
                case AND: result = y1 & z1;break;
                case XOR: result = y1 ^ z1;break;
            }
        }
        if (op instanceof ConditionExp.Op){
            switch ((ConditionExp.Op) op){
                case EQ: result = (y1 == z1);break;
                case NE: result = (y1 != z1);break;
                case LT: result = (y1 < z1);break;
                case GT: result = (y1 > z1);break;
                case LE: result = (y1 <= z1);break;
                case GE: result = (y1 >= z1);break;
            }
        }
        if (op instanceof ShiftExp.Op){
            switch ((ShiftExp.Op) op){
                case SHL: result = y1 << z1;break;
                case SHR: result = y1 >> z1;break;
                case USHR: result = y1 >>> z1;break;
            }
        }

        if (result instanceof Boolean){
            int value = (boolean)result?1:0;
            return Value.makeConstant(value);
        }

        return Value.makeConstant((int)result);

    }
}

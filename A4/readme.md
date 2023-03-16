<!-- toc -->

1. [tai-e A4 ，简单记录](#)
    1. [CHA](#)
        1. [方法签名](#)
        2. [virtual call](#)
        3. [`getSubClasses()`](#)
        4. [算法](#)
    2. [ICFG 的构建](#)
    3. [过程间，常量传播](#)
        1. [两类 transfer](#)
        2. [具体算法](#)

<!-- tocstop -->

# tai-e A4 ，简单记录

https://tai-e.pascal-lab.net/pa4.html

## CHA

主要参考了 https://github.com/rmb122/Tai-e-assignments 的实现，几乎是照抄的

有几个需要注意的点

### 方法签名

`MethodRef` 可以看做是 方法的签名（Signature），它由 DeclaringClass（class type） 和 Subsignature （method name + descriptor） 共同构成：

<img src="https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230227171946029.png" alt="image-20230227171946029" style="zoom:50%;" />



### virtual call 

java 中的 virtual call ，包含了 `invokeinterface` 和 `invokevirtual` 两种：

<img src="https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230227172140140.png" alt="image-20230227172140140" style="zoom:50%;" />



### `getSubClasses()`

`getSubClasses()` 方法，迭代地获取了一个类的所有 子类、孙类（总之是所有的子类），而不仅仅是 direct sub class



### 算法

算法直接参考课件的：

<img src="https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230227172729672.png" alt="image-20230227172729672" style="zoom:50%;" />

<img src="https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230227172705414.png" alt="image-20230227172705414" style="zoom:50%;" />

<img src="https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230227172634693.png" alt="image-20230227172634693" style="zoom:50%;" />





## ICFG 的构建

ICFG 的构建，位于 `ICFGBuilder` 的 `analyze()` 中，而且基于 一个已经构建好的 call graph ，在这个例子中，这个 call graph 就是通过 CHA 构建的：

![image-20230228120610524](https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230228120610524.png)

![image-20230228120811299](https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230228120811299.png)









## 过程间，常量传播

也是一个 forward analysis ，总体算法结构和 A2 中实现的 过程内 常量传播很相似

### 两类 transfer

但是过程间常量传播，需要两类 transfer ，node transfer function 和 edge transfer function ：

* node transfer function 用来由 IN[S] 得到 OUT[S]
* edge transfer function，用来处理 OUT[S1] 得到 NEWOUT[S1]，然后所有 NEWOUT meet 在一起，得到IN[S2] ，其中 S1->S2

实现的 api 包括：

```java
boolean transferCallNode(Stmt,CPFact,CPFact)
boolean transferNonCallNode(Stmt,CPFact,CPFact)
CPFact transferNormalEdge(NormalEdge,CPFact)
CPFact transferCallToReturnEdge(CallToReturnEdge,CPFact)
CPFact transferCallEdge(LocalEdge,CPFact)
CPFact transferReturnEdge(LocalEdge,CPFact)
```

### 具体算法

具体算法的实现，在 `InterSolver.doSolve()` 当中，是个 worklist 算法

和过程内的常量传播不同的点，在于在将 所有 前驱的 out fact meet 在一起，作为 in fact 之前，需要对这些 out fact 进行 `transferEdge()` 的处理，处理得到的 new out  fact 才能进行 meet ：

![image-20230227172909102](https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230227172909102.png)

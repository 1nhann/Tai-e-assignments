<!-- toc -->

1. [tai-e A1 ，实现和简单调试](#)
    1. [实现](#)
    2. [调试](#)
        1. [用 soot 处理源码：](#)
        2. [根据 `plan.yml` 进行分析：](#)
        3. [运行分析算法，进行初始化：](#)
        4. [运行分析算法，进行迭代：](#)
        5. [打印结果：](#)

<!-- tocstop -->

#  tai-e A1 ，实现和简单调试

https://tai-e.pascal-lab.net/pa1.html#_1-%E4%BD%9C%E4%B8%9A%E5%AF%BC%E8%A7%88

参考了 https://github.com/rmb122/Tai-e-assignments 的实现

## 实现

直接参考课程课件里给的算法，就很容易实现：

<img src="https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230223114458494.png" alt="image-20230223114458494" style="zoom: 33%;" />

## 调试

程序的入口是 `Assignment` 的 `main()` ，在其中进行了参数处理，然后调用了 `pascal.taie.Main` 的 `main()` ：
![image-20230223114623469](https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230223114623469.png)

这个 `Main` 位于 tai-e 教学版这个依赖，即  `tai-e-assignment.jar`  当中，这个 `tai-e-assignment.jar` 实际上是在 https://github.com/pascal-lab/Tai-e 的基础上，简化后编译得到的：

![image-20230223114745255](https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230223114745255.png)



### 用 soot 处理源码：

调用了 `buildWord()` ，跟进这个 `buildWorld()` ，可以看到，调用了 `SootWorldBuilder` 的 `build()` 方法，在其中调用了 soot 的 api ，实现了对待分析源码的 fronted 处理：

![image-20230223115235648](https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230223115235648.png)

![image-20230223115427722](https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230223115427722.png)





### 根据 `plan.yml` 进行分析：

然后调用了 `executePlan()` ，可以看到 plan 是一个 size 为 4 的 list ：

![image-20230223115800379](https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230223115800379.png)

而这四个 plan config ，来自 `plan.yml` 文件：

![image-20230223115850728](https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230223115850728.png)

然后就开始遍历这四个 plan config ，进行 execute ：

![image-20230223120111028](https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230223120111028.png)

其中，`cfg` 对应的是 `CFGBuilder` ，进行的是 method analysis ：

![image-20230223120338819](https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230223120338819.png)



随后来到 `livevar` 对应的 `LiveVariableAnalysis` ，这个类是作业自己实现的，继承自 `MethodAnalysis` ：

![image-20230223120714785](https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230223120714785.png)



跟进这个 `runMethodAnalysis()` ，可以看到遍历了每个方法 `m`（`m` 是 `JMethod` 对象），提取其对应的 IR，然后就调用了 `LiveVariableAnalysis` 对象的 `analyze()` 方法：

![image-20230223121004441](https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230223121004441.png)

跟进这个 `analyze()` ，可以看到来到了 `AbstractDataflowAnalysis` 的 `analyze()` ，直接从 ir 中取出了 cfg ，作为变量传入 `solver` 的 `solve()` 方法，这个 `solver` 是个 `IterativeSolver` 对象：

![image-20230223121337533](https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230223121337533.png)



### 运行分析算法，进行初始化：

跟进这个 `solve()` ，来到了 `Solver` 的 `solve()` ，在其中先是调用了 `initialize()` ，返回的是一个 `DataflowResult` 对象，这个变量就将是整个 分析的最终结果：

![image-20230223121726994](https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230223121726994.png)

跟进这个 `initialize()` ，可以看到判断了一下本次分析是 forward 还是 backward ，然后调用了对应的 initialize 方法，因为 Reaching Definitions Analysis 是个 backward analysis ，所以这里调用了 `initializeBackward()` ：

![image-20230223121852319](https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230223121852319.png)

跟进 `initializeBackward()`，这个方法是作业自己要写的，根据算法给 exit 这个 node 赋了个边界值，然后给其余 node 也赋了初始值，这个所谓的赋值操作都是对 `result` 这个 map 的操作：

![image-20230223122019186](https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230223122019186.png)





### 运行分析算法，进行迭代：

初始化结束后，调用 `doSolve()` 开始了迭代分析：

![image-20230223122437455](https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230223122437455.png)



跟进后，可以看到，也判断了一下这个分析是forward 还是 backward ，然后调用对应的 doSolve ：

![image-20230223122538105](https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230223122538105.png)

来到自己设计的 `doSolveBackward()` ，进行迭代分析，不断修改 `result` 对象，最终完成分析：

![image-20230223122646323](https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230223122646323.png)







### 打印结果：

在 `livevar` 这个 plan 结束后，还有一个 plan ，id 是 `process-result` ，这个 plan 负责打印分析结果：

![image-20230223122950914](https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230223122950914.png)
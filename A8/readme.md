<!-- toc -->

1. [tai-e A8 ，简单记录](#)
    1. [总体算法](#)
    2. [`processTaintInvoke()` 实现](#)
        1. [base as base](#)
        2. [base as arg](#)
    3. [数据结构](#)
    4. [`collectTaintFlows()` 实现](#)

<!-- tocstop -->

# tai-e A8 ，简单记录

参考了 https://github.com/rmb122/Tai-e-assignments 的思想，但是算法和数据结构都经过思考和理解后，进行了优化，更加完美

## 总体算法

taint analysis 的算法，基于 A6 的 C.S Pointer Analysis ，但是需要额外处理 关于 污点分析的调用 ：

<img src="https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230316142452737.png" alt="image-20230316142452737" style="zoom:50%;" />

![image-20230316142531026](https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230316142531026.png)



## `processTaintInvoke()` 实现

`processTaintInvoke()` 主要处理了两种形式的 invoke ：

```
l: r = base.k(a1,...,an)
l: r = y.k(base,...,an)
l: r = y.k(a1,base,...,an)
```

![image-20230316142639873](https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230316142639873.png)



### base as base

处理 `l: r = base.k(a1,...,an)` 这种类型的 invoke 的逻辑主要位于 `taintAnalysis.processTaintInvoke()` 中

在 `taintAnalysis.processTaintInvoke()` 中，又根据调用方法是 `source` 、`base-to-result transfer` 、`arg-to-result transfer` 还是 `arg-to-base transfer`  有各自的处理：

![image-20230316143040926](https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230316143040926.png)



处理的逻辑基于作业说明中的描述：

![image-20230316143306152](https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230316143306152.png)

![image-20230316143253964](https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230316143253964.png)







在 `taintAnalysis.processSink()` 中，主要是做了一下记录，如果当前访问的 invoke 是 sink 的话，就把sink和当前的 cs callsite 对应起来，目的是为了 后面的 `collectTaintFlows()` 服务



### base as arg

这一部分的算法 作业说明中并没有讲，主要逻辑是找到 所有以 base 作为 arg 的 invoke ，对它们再进行 处理

base as arg 不可或缺，如果不做这个处理的话有的测试用例就过不了

> 这一部分的处理是参考的 rmb122 ，他的代码中有类似的 `processArg()` 方法

![image-20230316143759019](https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230316143759019.png)



## 数据结构

用了几个 tai-e 中自己实现的数据结构，比如 `TwoKeyMap` 和 `MultiMap` ，用来存储 transfer 还有 sink、souce 相关的信息，其中 `sink2cscallsites` 用以 在 `collectTaintFlows()` 中调用 ，生成最终的分析结果：

![image-20230316143920134](https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230316143920134.png)



## `collectTaintFlows()` 实现

实现逻辑参考作业说明：

![image-20230316144308958](https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230316144308958.png)



具体实现的时候，用到了 `MultiMap` 这个数据结构（这个数据结构是个 map ，但是 value 是个 set ），用来创建 `sink2cscallsites` 

具体实现的算法，就是遍历 `sink2cscallsites` 中的所有 sink ，取出每个 sink 的所有 cs callsite ，比如 `l: r = x.k(a1,...,an)` ，然后取出 当前 context 下的 `a[sink index]` 的 point to set ，看看其中有没有 taint obj ，如果有的话，就能生成一个 TaintFlow ，最终所有 TaintFlow 的集合就是分析的结果：

![image-20230316144236082](https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230316144236082.png)

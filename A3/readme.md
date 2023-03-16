<!-- toc -->

1. [tai-e A3 ，简单记录](#)
    1. [还是过程内分析](#)
    2. [具体算法实现](#)

<!-- tocstop -->

# tai-e A3 ，简单记录

https://tai-e.pascal-lab.net/pa3.html

## 还是过程内分析

实现的 死代码检测，依然是过程内分析，在代码中的体现就是 `DeadCodeDetection` 这个类是 `MethodAnalysis` 的子类：

![image-20230225162728748](https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230225162728748.png)





## 具体算法实现

https://tai-e.pascal-lab.net/pa3.html#_2-死代码检测介绍

具体算法的设计，参考作业的文档，主要是在实现了 Live Variable Analysis 和 Constant Propagation 之后，在其结果之上实现了 Dead Code Detection 

主要设计了两个方法 `getReachableCode()` 和 `getDeadAssignStmt()`，这两个方法的设计思路也参考的作业文档：

![image-20230225163115842](https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230225163115842.png)

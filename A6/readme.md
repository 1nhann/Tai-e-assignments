<!-- toc -->

1. [tai-e A6 ，简单记录](#)
    1. [主算法](#)
    2. [Context Selector](#)
        1. [`selectHeapContext() ` 获取 abstract object 的 context](#)
        2. [`selectHeapContext()` 的实现逻辑](#)
        3. [与课件算法的出入](#)
    3. [处理 静态field、静态方法、数组](#)
    4. [调试](#)
        1. [指定 上下文敏感 变种：](#)

<!-- tocstop -->

# tai-e A6 ，简单记录

参考 https://github.com/rmb122/Tai-e-assignments



## 主算法

直接参考课件上给的算法：

<img src="https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230308212706488.png" alt="image-20230308212706488" style="zoom:50%;" />



其中有个 `Select()` 函数 

基于 cloning based 的思想，为了确定调用的 method ，除了用  `Dispatch()` 找到 method 的 **签名（signature）** 之外，还要调用 Select 找到 method 对应的 **上下文（context）** 



## Context Selector

<img src="https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230308214410534.png" alt="image-20230308214410534" style="zoom:50%;" />

具体实现了 6 个 context selector ，对应 6 种 上下文敏感指针分析的变种：

| variant name            | class name     |
| ----------------------- | -------------- |
| 1-call-site sensitivity | _1CallSelector |
| 2-call-site sensitivity | _2CallSelector |
| 1-object sensitivity    | _1ObjSelector  |
| 2-object sensitivity    | _2ObjSelector  |
| 1-type sensitivity      | _1TypeSelector |
| 2-type sensitivity      | _2TypeSelector |

实际上这些变种对应着 context 的不同表现形式

每个 selector 除了实现 `selectContext()` 方法，用以获取 method 的 context 之外

###  `selectHeapContext() ` 获取 abstract object 的 context 

还实现了一个 `selectHeapContext()` ，用以获取 abstract object 的 context ，并在 处理 new 语句的时候被调用：

![image-20230308214616776](https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230308214616776.png)

![image-20230308214646636](https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230308214646636.png)

###   `selectHeapContext()`  的实现逻辑

这个  `selectHeapContext()`  的实现逻辑，在 作业说明中有提及：

<img src="https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230308214922037.png" alt="image-20230308214922037" style="zoom:67%;" />



### 与课件算法的出入

而且 由于   `selectHeapContext()`   这个方法的调用，课件中描述的算法和具体的实现有一些出入：

![image-20230308215511807](https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230308215511807.png)

![image-20230308215711203](https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230308215711203.png)





## 处理 静态field、静态方法、数组

可以参考 A5 的实现：[[tai-e A5 ，简单记录]]

具体的 Rules 也在 作业说明中有：

<img src="https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230308215836903.png" alt="image-20230308215836903" style="zoom: 67%;" />

<img src="https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230308215901266.png" alt="image-20230308215901266" style="zoom:67%;" />

<img src="https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230308215920836.png" alt="image-20230308215920836" style="zoom:67%;" />





## 调试

首先执行 cspta 这个 plan ，进入 `CSPTA` 的 `analyze()` 方法，在其中初始化了 `Solver` 对象，根据 `cs` 这个 option ，传入一个 context selector ，在初始化完 `Solver` 对象后，调用其 `solve()` 方法：

![image-20230309131053592](https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230309131053592.png)

跟进这个 `solve()` 方法，可以看到运行的代码有两大步骤，第一步是 进行 初始化 `initialize()` ，第二步是正式的分析：

![image-20230309131413246](https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230309131413246.png)



### 指定 上下文敏感 变种：

运行 Test 中的 `testOneCall()` ，可以看到，在初始化 context selector 的时候，传入的 cs 参数是 `1-call` ：

![image-20230309131839651](https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230309131839651.png)

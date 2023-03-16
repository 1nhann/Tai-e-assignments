<!-- toc -->

1. [tai-e A2 ，简单记录](#)
    1. [Transfer Function](#)
    2. [Worklist 算法](#)

<!-- tocstop -->

# tai-e A2 ，简单记录

参考了 https://github.com/rmb122/Tai-e-assignments 的实现

但是 `WorkListSolver.doSolveForward()` 和 `ConstantPropagation.transferNode()` 这两个方法的实现，是自己思考调试得到的，和 rmb122 的有比较大的不同，但是更还原 课件给的算法，更好理解

这里就记录几个重点



## Transfer Function

算法参照 课件，我的代码实现可以说是完美还原了课件给的 Transfer Function：

<img src="https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230224221748799.png" alt="image-20230224221748799" style="zoom: 50%;" />







## Worklist 算法

worklist 算法是 迭代算法（A1 采用的）的优化，形式类似于：

<img src="https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230224222113559.png" alt="image-20230224222113559" style="zoom: 67%;" />

具体的代码实现，采用了 队列（LinkedList） 来实现 worklist 这个数据结构

对于 forward 算法来说，如果 经过 transfer function 处理后，out 改变了，就将当前 node 的所有后继重新append 到 worklist 当中

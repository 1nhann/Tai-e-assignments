<!-- toc -->

1. [tai-e A5 ，简单记录](#)
    1. [算法](#)
    2. [处理 静态field、静态方法、数组](#)
        1. [Rule](#)
        2. [具体实现](#)

<!-- tocstop -->

# tai-e A5 ，简单记录

https://tai-e.pascal-lab.net/pa5.html

实现了一个全程序指针分析

## 算法

大体的算法直接参考课件：

<img src="https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230303120548441.png" alt="image-20230303120548441" style="zoom:50%;" />

<img src="https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230303120527015.png" alt="image-20230303120527015" style="zoom:50%;" />



## 处理 静态field、静态方法、数组

### Rule

作业说明里面给了处理的 Rule ：

<img src="https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230303120802773.png" alt="image-20230303120802773" style="zoom:50%;" />



<img src="https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230303120815916.png" alt="image-20230303120815916" style="zoom:50%;" />



<img src="https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230303120825684.png" alt="image-20230303120825684" style="zoom:50%;" />



### 具体实现

具体实现的时候，静态 feild 的 load 和 store ，还有静态方法的调用，在 `addReachable()` 中被处理了，它们都不涉及 point-to relations ，即和 new 还有 assgin 一样，不需要从 pts 里面拿信息：

![image-20230303121339156](https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230303121339156.png)

而数组元素的 load 和 store ，和 instance field 的 load 和 store 处理类似，所以放在 `Solve()` 里面：

![image-20230303121502460](https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230303121502460.png)

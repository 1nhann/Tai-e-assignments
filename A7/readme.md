<!-- toc -->

1. [tai-e A7 ，简单记录](#)
    1. [A4过程间常量传播的缺憾](#)
    2. [使用 Alias-Aware 的常量传播](#)
    3. [Alias 信息初始化：](#)
    4. [transferLoadField](#)
    5. [transferLoadArray](#)
    6. [transferStoreField](#)
    7. [transferStoreArray](#)

<!-- tocstop -->

# tai-e A7 ，简单记录

https://tai-e.pascal-lab.net/pa7.html

参考了 https://github.com/rmb122/Tai-e-assignments 的大部分代码

这里记录几个重要的点

## A4过程间常量传播的缺憾

过程间常量传播算法，具体实现在 A4 ，可以参考 [[tai-e A4 ，简单记录]]

复习 A4 的代码实现，可以看到在处理 non call node 的时候，是直接采用的 过程内常量传播的方式来对 node 进行 transfer ：

![image-20230310214232809](https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230310214232809.png)

而在这个过程内常量传播的 transfer function 中，只处理了 LValue 是 Var 的情况，且对于 RValue 是 Field Access 和 Array Access 的情况，直接视为 NAC ：

![image-20230310214408820](https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230310214408820.png)







## 使用 Alias-Aware 的常量传播

在处理 Non Call Node 的时候，对于 LoadField 、LoadArray、StoreField、StoreArray 语句做特殊的处理：

> 这里需要注意，用 soot 处理得到的 IR ，不会出现 `x.f = y.g` 这样的赋值，等号左右两边至少有一个 var ，比如 `x.f = y` 、`y = x.f`

![image-20230310214710953](https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230310214710953.png)





## Alias 信息初始化：

在 `InterConstantPropagation` 的 `initializeAlias()` 方法中，实现了 alias 信息的初始化，生成一个 MultiMap 对象（value 是个 set ）

> 需要注意的是，这里用的是 Var 而不是 CSVar 

![image-20230310215732781](https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230310215732781.png)

具体实现的逻辑，参考作业说明：

![image-20230310215842586](https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230310215842586.png)

代码中的实现，是先构建一个 object 到 vars 的 MultiMap ，然后这个 map 中，每个 object 对应的 set 中的 vars 就互为别名



## transferLoadField

```
y = T.f
y = x.f
```

对于 load field 语句，需要考虑 static 和 instance 两种情况：

![image-20230310215104373](https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230310215104373.png)

具体的实现逻辑可以参考作业说明：

![image-20230310215244723](https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230310215244723.png)

![image-20230310215319418](https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230310215319418.png)



总体思路，可以认为是，先获取 RValue（`T.f` 或者 `x.f`）的所有 Store 语句（`T.f = y` 或者 `x.f = y` ），然后计算 meet 所有 `y` ，最终得到 常量传播的结果





## transferLoadArray

```
y = x[i]
```

对于 load array 语句的处理，也可以参考 作业说明：

![image-20230310220633027](https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230310220633027.png)

![image-20230310220754003](https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230310220754003.png)



主要思路是，首先获取 `x[i]` 中的 `x` 和 `i` 这两个 var ，然后计算 `i` 此时的 value ，随后调用 `x` 的 `getStoreArrays()` 获取得到所有 store arrays 语句， 并计算 array access 中的 index 的 value 是不是和上面计算的 `i` 的 value 相等，如果是的话，才是真正需要考虑的 store array 语句



## transferStoreField

```
T.f = y
x.f = y
```

store field 语句的实现，作业说明中并没有过多提及，主要的设计思路是，找到 `T.f` 和 `x.f` 所影响的那些 load field 语句，比如  `a = T.f` 、`a = x.f` ，然后将这些语句 append 到 worklist 当中，等调用 `transferLoadField()` 的时候再处理：

![image-20230310221804455](https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230310221804455.png)



## transferStoreArray

```
y = x[i]
```

对于 array 的 store 语句的处理，和 store field 一样，都是把对应的 load 语句找出来，加到 worklist 里面，等到在 `transferLoadArray()` 中进行：

![image-20230310222323219](https://raw.githubusercontent.com/1nhann/hub/master/data/blog/2023/03/image-20230310222323219.png)

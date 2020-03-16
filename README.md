# W3G Viewer
Java 解析《魔兽争霸3》游戏录像工具。
## 使用方法
执行主类 `io.github.kayodesu.w3gviewer.W3GViewer` 即可，要解析的录像路径以输入参数的形式传入。  
例如解析 `D:\war3\replay\2.w3g`
```
C:\>java io.github.kayodesu.w3gviewer.W3GViewer D:\war3\replay\2.w3g
```
如使用IDE，在IDE里面直接配置输入参数即可。 

---
 
一份输出样例：
```
Warcraft III recorded game
文件路径：D:\war3\replay\2.w3g
文件大小：139.31KB
版本：W3XP 1.26.6059
时长：30:35
游戏名称：当地局域网内的游戏 (ka
游戏地图：Maps\(4)LostTemple.w3m
游戏创建者：kayo

---玩家0---
名称：kayo
是否电脑玩家：否
队伍：0
颜色：灰色
种族：不死族
障碍（血量）：100
游戏时间：30:35
操作次数：2489
APM：81

---玩家1---
名称：null
是否电脑玩家：是(令人发狂的)
队伍：0
颜色：橘黄色
种族：兽族
障碍（血量）：100

---玩家2---
名称：null
是否电脑玩家：是(令人发狂的)
队伍：1
颜色：青色
种族：暗夜精灵
障碍（血量）：100

---玩家3---
名称：null
是否电脑玩家：是(令人发狂的)
队伍：1
颜色：紫色
种族：人族
障碍（血量）：100

[0:10]kayo 对 盟友 说：gl hf
[1:24]kayo 对 盟友 说：fjeijfi j fjefefe
[11:22]kayo 对 盟友 说：jfejfieje
[11:40]kayo 对 盟友 说：fkeofjefe jfejfie
[16:00]kayo 对 盟友 说：rdffffw
[16:59]kayo 对 盟友 说：112f jfe ojfoejfefje
[21:07]kayo 对 盟友 说：good job
[21:27]kayo 对 盟友 说：fffffef
[23:17]kayo 对 盟友 说：julkjkjkyp
[30:59]kayo 对 盟友 说：will win
```
## 参考文档
http://w3g.deepnode.de/files/w3g_format.txt  
http://w3g.deepnode.de/files/w3g_actions.txt

这两篇文档也可以在 `工程目录/doc/` 下找到。
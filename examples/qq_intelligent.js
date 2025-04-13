/*
METADATA
{
    // QQ Intelligent Assistant Package
    name: qq_intelligent
    description: 高级QQ智能助手，通过UI自动化技术实现QQ应用交互，支持消息自动回复、历史记录读取、联系人搜索与通讯等功能，为AI赋予QQ社交能力。适用于智能客服、自动回复、社交辅助等场景。

    // Tools in this package
    tools: [
        {
            name: reply
            description: 在当前聊天窗口输入消息并发送。一般情况下，用户想要ai帮忙发送消息时，需要ai自己去生成回复的消息，如果不确定发送的内容，请不要调用工具。只要是停留在聊天界面，就可以直接调用这个。
            parameters: [
                {
                    name: message
                    description: 要发送的消息
                    type: string
                    required: true
                },
                {
                    name: click_send
                    description: 是否点击发送按钮
                    type: boolean
                    required: false
                }
            ]
        },
        {
            name: find_user
            description: 在QQ联系人或群成员中查找用户
            parameters: [
                {
                    name: user_name
                    description: 搜索用户名称
                    type: string
                    required: true
                },
                {
                    name: user_type
                    description: 搜索类型（contacts/groups）
                    type: string
                    required: true
                }
            ]
        },
        {
            name: find_and_reply
            description: 查找用户并发送消息
            parameters: [
                {
                    name: message
                    description: 要发送的消息
                    type: string
                    required: true
                },
                {
                    name: user_name
                    description: 发送目标用户名称
                    type: string
                    required: true
                },
                {
                    name: user_type
                    description: 用户类型（contacts/groups）
                    type: string
                    required: true
                },
                {
                    name: click_send
                    description: 是否点击发送按钮
                    type: boolean
                    required: false
                }
            ]
        },
        {
            name: get_history
            description: 获取当前聊天窗口的历史消息。如果用户要求读取聊天记录，那么可以用这个工具或者find_and_get_history。
            parameters: [
                {
                    name: message_num
                    description: 获取的消息数量
                    type: number
                    required: false
                }
            ]
        },
        {
            name: find_and_get_history
            description: 查找用户并获取聊天历史记录。一般情况下，用户想要让ai帮忙回消息的时候，需要先调用这个，得到历史记录后直接调用reply。
            parameters: [
                {
                    name: user_name
                    description: 搜索用户名称
                    type: string
                    required: true
                },
                {
                    name: user_type
                    description: 搜索类型（contacts/groups）
                    type: string
                    required: true
                },
                {
                    name: message_num
                    description: 获取的消息数量
                    type: number
                    required: false
                }
            ]
        }
    ],
    "category": "UI_AUTOMATION"
}
*/
const QQIntelligent = (function () {
    async function close_keyboard() {
        await Tools.UI.pressKey("KEYCODE_BACK");
    }
    async function is_in_group() {
        let page = await UINode.getCurrentPage();
        return page.findAllByContentDesc('语音').findIndex(e => { var _a, _b, _c; return ((_a = e.className) === null || _a === void 0 ? void 0 : _a.includes('ImageButton')) && ((_c = (_b = e.centerPoint) === null || _b === void 0 ? void 0 : _b.y) !== null && _c !== void 0 ? _c : 0) > 1000; }) === -1;
    }
    Array.prototype.at = function (index) {
        if (index < 0) {
            index = this.length + index;
        }
        return this[index];
    };
    async function get_history(params) {
        var _a, _b, _c, _d, _e, _f, _g;
        const message_num = params.message_num || 10;
        let page = await UINode.getCurrentPage();
        //获取群名称
        const chat_title = (_c = (_b = (_a = page.findById('com.tencent.mobileqq:id/ivTitleBtnLeft')) === null || _a === void 0 ? void 0 : _a.parent) === null || _b === void 0 ? void 0 : _b.allTexts()[0]) !== null && _c !== void 0 ? _c : "";
        // const is_group = await is_in_group();
        console.log("chat_title", chat_title);
        // console.log("is_group", is_group);
        //先滑动到最底部
        let tryMax = 0;
        while (tryMax < 1) {
            tryMax++;
            await Tools.UI.swipe(100, 1000, 100, 200);
            await Tools.System.sleep(500);
        }
        let messageList = [];
        let allMessages = [];
        //获取历史消息
        tryMax = 0;
        while (tryMax < 5) {
            tryMax++;
            page = await UINode.getCurrentPage();
            console.log("page", page.toFormattedString());
            const list_view = page.findByClass('RecyclerView');
            if (!list_view) {
                return undefined;
            }
            // 清空临时消息列表
            messageList = [];
            // 获取当前可见的消息列表
            for (const message of list_view.children) {
                let message_text = message.allTexts().join('/');
                let sender = "other";
                //判断头像再左边还是右边
                const avatar = message.findByClass('ImageView');
                if (avatar) {
                    if (((_e = (_d = avatar.centerPoint) === null || _d === void 0 ? void 0 : _d.x) !== null && _e !== void 0 ? _e : 0) < 300) {
                        sender = "other";
                        console.log("other", (_f = avatar.centerPoint) === null || _f === void 0 ? void 0 : _f.x);
                    }
                    else {
                        sender = "self";
                        console.log("self", (_g = avatar.centerPoint) === null || _g === void 0 ? void 0 : _g.x);
                    }
                }
                else {
                    sender = "self";
                    console.log("self");
                }
                messageList.push({ message: message_text, sender: sender });
            }
            // 将当前视图中的消息添加到总集合中，保持顺序
            // 从下往上滑动时，将新获取的消息放在前面（保持旧消息在前，新消息在后的顺序）
            allMessages = [...messageList, ...allMessages];
            // 如果已经获取了足够多的消息，停止滚动
            if (allMessages.length >= message_num) {
                break;
            }
            await Tools.UI.swipe(100, 300, 100, 1000);
            await Tools.System.sleep(500);
        }
        // 删除重复的消息，但保持原始顺序
        const uniqueMessages = [];
        const seen = new Set();
        for (const msg of allMessages) {
            const key = `${msg.message}-${msg.sender}`;
            if (!seen.has(key)) {
                seen.add(key);
                uniqueMessages.push(msg);
            }
        }
        // 返回符合数量要求的消息列表（保持原始顺序）
        return { messages: uniqueMessages.slice(0, message_num), chat_title: chat_title };
    }
    async function reply(params) {
        // 提取参数
        const message = params.message || "";
        const click_send = params.click_send || false;
        await Tools.UI.setText(message);
        await Tools.System.sleep(500);
        if (click_send) {
            await Tools.UI.clickElement({
                resourceId: "com.tencent.mobileqq:id/send_btn",
                index: "0"
            });
        }
        return true;
    }
    async function find_and_reply(params) {
        // 提取参数
        const message = params.message || "";
        const user_name = params.user_name || "";
        const user_type = params.user_type || "contacts";
        const click_send = params.click_send || false;
        let result = await find_user({ user_name: user_name, user_type: user_type });
        if (!result) {
            return false;
        }
        await Tools.System.sleep(1000);
        return await reply({ message: message, click_send: click_send });
    }
    async function ensureActivity(activityName = "", packageName = "com.tencent.mobileqq", enterActivity = async () => true, tryMax = 1) {
        var _a, _b;
        let android = new Android();
        let activity = await Tools.UI.getPageInfo();
        if ((_a = activity.activityName) === null || _a === void 0 ? void 0 : _a.includes(activityName)) {
            return true;
        }
        while (tryMax > 0) {
            tryMax--;
            await Tools.System.stopApp(packageName);
            await Tools.System.sleep(2000);
            await Tools.System.startApp(packageName);
            // let intent = android.createIntent();
            // intent.setComponent(packageName, activityName);
            // await intent.start();
            await Tools.System.sleep(3000); // Give some time for app to launch
            if (await enterActivity()) {
                activity = await Tools.UI.getPageInfo();
                if ((_b = activity.activityName) === null || _b === void 0 ? void 0 : _b.includes(activityName)) {
                    return true;
                }
            }
        }
        return false;
    }
    async function find_user(params) {
        // 提取参数
        const user_name = params.user_name || "";
        const user_type = params.user_type || "contacts";
        if (!await ensureActivity("com.tencent.mobileqq.search.activity.UniteSearchActivity", "com.tencent.mobileqq", async () => {
            const search_btn = (await UINode.getCurrentPage()).findByText("搜索");
            if (search_btn) {
                await search_btn.click();
                return true;
            }
            return false;
        })) {
            return false;
        }
        let firstTarget = undefined;
        let tryMax = 0;
        while (tryMax < 2) {
            tryMax++;
            const search_btn = (await UINode.getCurrentPage()).findByText("搜索");
            if (search_btn) {
                await search_btn.click();
                await Tools.System.sleep(500);
            }
            // 输入搜索内容
            await Tools.UI.setText(user_name);
            await Tools.System.sleep(3000 * tryMax);
            await close_keyboard();
            const currentPage = await UINode.getCurrentPage();
            const searchResult = currentPage.findAllById('com.tencent.mobileqq:id/title');
            let isNeedToCatch = false;
            for (const child of searchResult) {
                if (isNeedToCatch) {
                    firstTarget = child;
                    break;
                }
                // const title = child.findById('com.tencent.mobileqq:id/title');
                const title = child;
                if (title) {
                    if (user_type == "contacts" && title.text == "联系人") {
                        isNeedToCatch = true;
                    }
                    else if (user_type == "groups" && title.text == "群聊") {
                        isNeedToCatch = true;
                    }
                }
            }
            if (firstTarget) {
                await firstTarget.click();
                return true;
            }
        }
        return false;
    }
    async function find_and_get_history(params) {
        const user_name = params.user_name || "";
        const user_type = params.user_type || "contacts";
        const message_num = params.message_num || 10;
        let result = await find_user({ user_name: user_name, user_type: user_type });
        if (!result) {
            return undefined;
        }
        await Tools.System.sleep(1000);
        return await get_history({ message_num: message_num });
    }
    async function main() {
        // let result = await find_and_reply({ message: "你好你好！我是OPERIT，很高兴认识你！", user_name: "韩韩韩", user_type: "contacts", click_send: true });
        // await find_user({ user_name: "Wind", user_type: "contacts" });
        // await find_user({ user_name: "Dec", user_type: "groups" });
        // await Tools.System.sleep(1000);
        let result = await find_and_get_history({ user_name: "Dec", user_type: "groups", message_num: 20 });
        console.log(result);
        complete({
            success: result
        });
    }
    async function wrap_bool(func, params, successMessage, failMessage, additionalMessage = "") {
        if (await func(params)) {
            complete({
                success: true,
                message: successMessage,
                additionalMessage: additionalMessage
            });
        }
        else {
            complete({
                success: false,
                message: failMessage,
                additionalMessage: additionalMessage
            });
        }
    }
    async function wrap_data(func, params, successMessage, failMessage, additionalMessage = "") {
        const result = await func(params);
        complete({
            success: true,
            message: successMessage,
            additionalMessage: additionalMessage,
            data: result
        });
    }
    return {
        main: main,
        reply: async (params) => await wrap_bool(reply, params, "发送成功", "发送失败"),
        find_user: async (params) => await wrap_bool(find_user, params, "查找成功", "查找失败，停留在界面", (await UINode.getCurrentPage()).toFormattedString()),
        find_and_reply: async (params) => await wrap_bool(find_and_reply, params, "发送成功", "发送失败，停留在界面", (await UINode.getCurrentPage()).toFormattedString()),
        get_history: async (params) => await wrap_data(get_history, params, "获取历史消息成功", "获取历史消息失败"),
        find_and_get_history: async (params) => await wrap_data(find_and_get_history, params, "获取历史消息成功", "获取历史消息失败")
    };
})();
//逐个导出
exports.reply = QQIntelligent.reply;
exports.find_user = QQIntelligent.find_user;
exports.find_and_reply = QQIntelligent.find_and_reply;
exports.get_history = QQIntelligent.get_history;
exports.find_and_get_history = QQIntelligent.find_and_get_history;
exports.main = QQIntelligent.main;

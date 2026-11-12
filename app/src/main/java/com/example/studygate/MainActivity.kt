package com.example.studygate

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
                    AppNavigation()
                }
            }
        }
    }
}

// ================== 数据模型 ==================
data class Message(val role: String, val content: String)
data class ChatSession(
    val id: String,
    var title: String,
    var messages: MutableList<Message>,
    var timestamp: Long,
    var isPinned: Boolean = false,
    var model: String = ""
)

fun ChatSession.toJson(): JSONObject {
    val msgsJson = JSONArray()
    for (m in messages) msgsJson.put(JSONObject().put("role", m.role).put("content", m.content))
    return JSONObject()
        .put("id", id).put("title", title).put("messages", msgsJson)
        .put("timestamp", timestamp).put("isPinned", isPinned).put("model", model)
}

fun parseSessions(jsonStr: String?): List<ChatSession> {
    if (jsonStr.isNullOrBlank()) return emptyList()
    val list = mutableListOf<ChatSession>()
    try {
        val arr = JSONArray(jsonStr)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val msgs = mutableListOf<Message>()
            val msgsArr = obj.getJSONArray("messages")
            for (j in 0 until msgsArr.length()) {
                val mObj = msgsArr.getJSONObject(j)
                msgs.add(Message(mObj.getString("role"), mObj.getString("content")))
            }
            list.add(ChatSession(
                id = obj.getString("id"),
                title = obj.getString("title"),
                messages = msgs,
                timestamp = obj.getLong("timestamp"),
                isPinned = obj.optBoolean("isPinned", false),
                model = obj.optString("model", "")
            ))
        }
    } catch (e: Exception) { e.printStackTrace() }
    return list
}

// ================== 全局导航与状态管理 ==================
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var globalBaseUrl by remember { mutableStateOf("https://api.deepseek.com/v1") }
    var globalApiKey by remember { mutableStateOf("") }
    var globalModel by remember { mutableStateOf("") }
    var isLoaded by remember { mutableStateOf(false) }

    var sessions by remember { mutableStateOf<List<ChatSession>>(emptyList()) }
    var currentSessionId by remember { mutableStateOf<String?>(null) }

    fun saveAllToDataStore() {
        scope.launch {
            val jsonArr = JSONArray().apply { sessions.forEach { put(it.toJson()) } }
            context.dataStore.edit {
                it[SettingsHelper.KEY_SESSIONS] = jsonArr.toString()
                it[SettingsHelper.KEY_BASE_URL] = globalBaseUrl
                it[SettingsHelper.KEY_API_KEY] = globalApiKey
                it[SettingsHelper.KEY_MODEL] = globalModel
            }
        }
    }

    LaunchedEffect(Unit) {
        val prefs = context.dataStore.data.first()
        globalBaseUrl = prefs[SettingsHelper.KEY_BASE_URL] ?: "https://api.deepseek.com/v1"
        globalApiKey = prefs[SettingsHelper.KEY_API_KEY] ?: ""
        globalModel = prefs[SettingsHelper.KEY_MODEL] ?: ""
        sessions = parseSessions(prefs[SettingsHelper.KEY_SESSIONS])
        if (sessions.isNotEmpty()) currentSessionId = sessions.first().id
        isLoaded = true
    }

    if (!isLoaded) return

    NavHost(navController = navController, startDestination = "chat") {
        composable("chat") {
            ChatScreen(
                navController = navController,
                sessions = sessions,
                currentSessionId = currentSessionId,
                globalBaseUrl = globalBaseUrl,
                globalApiKey = globalApiKey,
                onUpdateSessions = { sessions = it; saveAllToDataStore() },
                onSwitchSession = { currentSessionId = it }
            )
        }
        composable("setup") {
            SetupScreen(
                navController = navController,
                initialBaseUrl = globalBaseUrl,
                initialApiKey = globalApiKey,
                initialModel = globalModel,
                onSave = { title, baseUrl, apiKey, model ->
                    globalBaseUrl = baseUrl
                    globalApiKey = apiKey
                    globalModel = model

                    val newSession = ChatSession(
                        id = UUID.randomUUID().toString(),
                        title = if (title.isBlank()) "新对话" else title,
                        messages = mutableListOf(),
                        timestamp = System.currentTimeMillis(),
                        model = model
                    )
                    sessions = listOf(newSession) + sessions
                    currentSessionId = newSession.id
                    saveAllToDataStore()
                    navController.popBackStack()
                }
            )
        }
    }
}

// ================== 配置页面 ==================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    navController: NavController,
    initialBaseUrl: String,
    initialApiKey: String,
    initialModel: String,
    onSave: (String, String, String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf(initialBaseUrl) }
    var apiKey by remember { mutableStateOf(initialApiKey) }
    var model by remember { mutableStateOf(initialModel) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("新建对话", fontSize = 16.sp, fontWeight = FontWeight.Medium) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color(0xFF1A1A1A))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color.White
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val textFieldColors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF1A73E8),
                unfocusedBorderColor = Color(0xFFE0E0E0),
                focusedLabelColor = Color(0xFF1A73E8),
                unfocusedLabelColor = Color(0xFF888888),
                cursorColor = Color(0xFF1A73E8)
            )
            val textFieldShape = RoundedCornerShape(12.dp)

            OutlinedTextField(
                value = title, onValueChange = { title = it }, label = { Text("对话名称", fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth(), textStyle = TextStyle(fontSize = 12.sp), singleLine = true,
                shape = textFieldShape, colors = textFieldColors
            )
            OutlinedTextField(
                value = baseUrl, onValueChange = { baseUrl = it }, label = { Text("Base URL", fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth(), textStyle = TextStyle(fontSize = 12.sp), singleLine = true,
                shape = textFieldShape, colors = textFieldColors
            )
            OutlinedTextField(
                value = apiKey, onValueChange = { apiKey = it }, label = { Text("API Key (随时可改)", fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth(), textStyle = TextStyle(fontSize = 12.sp), singleLine = true,
                shape = textFieldShape, colors = textFieldColors
            )
            OutlinedTextField(
                value = model, onValueChange = { model = it }, label = { Text("Model (选定后不可改)", fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth(), textStyle = TextStyle(fontSize = 12.sp), singleLine = true,
                shape = textFieldShape, colors = textFieldColors
            )

            Button(
                onClick = { onSave(title, baseUrl, apiKey, model) },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A73E8))
            ) { Text("保存并进入对话", fontSize = 14.sp) }
        }
    }
}

// ================== 聊天主页 ==================
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    navController: NavController,
    sessions: List<ChatSession>,
    currentSessionId: String?,
    globalBaseUrl: String,
    globalApiKey: String,
    onUpdateSessions: (List<ChatSession>) -> Unit,
    onSwitchSession: (String?) -> Unit
) {
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val isImeVisible = WindowInsets.isImeVisible

    var input by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isSearchEnabled by remember { mutableStateOf(false) }

    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    var showRenameDialogFor by remember { mutableStateOf<ChatSession?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var showDeleteDialogFor by remember { mutableStateOf<ChatSession?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val currentSession = sessions.find { it.id == currentSessionId }
    val messages = currentSession?.messages ?: emptyList()
    val currentTitle = currentSession?.title ?: "新对话"

    val textPrimary = Color(0xFF1A1A1A)
    val textSecondary = Color(0xFF888888)
    val userBubbleColor = Color(0xFFE8F0FE)
    val placeholderColor = Color(0xFFCCCCCC)

    val isDrawerOpen = drawerState.currentValue == DrawerValue.Open || drawerState.targetValue == DrawerValue.Open

    LaunchedEffect(isImeVisible) {
        if (!isImeVisible) focusManager.clearFocus()
    }

    // 重命名弹窗
    if (showRenameDialogFor != null) {
        Dialog(onDismissRequest = { showRenameDialogFor = null }) {
            Surface(shape = RoundedCornerShape(16.dp), color = Color.White, modifier = Modifier.width(260.dp)) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("重命名对话", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = textPrimary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFF5F5F5), modifier = Modifier.fillMaxWidth().height(40.dp)) {
                        BasicTextField(
                            value = renameInput,
                            onValueChange = { renameInput = it },
                            textStyle = TextStyle(fontSize = 12.sp, color = textPrimary),
                            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp).wrapContentHeight(Alignment.CenterVertically),
                            singleLine = true,
                            decorationBox = { innerTextField ->
                                if (renameInput.isEmpty()) Text("输入新名字", color = textSecondary, fontSize = 12.sp)
                                innerTextField()
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { showRenameDialogFor = null },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF0F0F0))
                        ) { Text("取消", color = textSecondary, fontSize = 12.sp) }
                        Button(
                            onClick = {
                                val target = showRenameDialogFor!!
                                target.title = if (renameInput.isBlank()) "未命名" else renameInput
                                onUpdateSessions(sessions.map { if (it.id == target.id) target else it })
                                showRenameDialogFor = null
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A73E8))
                        ) { Text("确定", color = Color.White, fontSize = 12.sp) }
                    }
                }
            }
        }
    }

    // 删除确认弹窗
    if (showDeleteDialogFor != null) {
        Dialog(onDismissRequest = { showDeleteDialogFor = null }) {
            Surface(shape = RoundedCornerShape(16.dp), color = Color.White, modifier = Modifier.width(260.dp)) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("永久删除对话", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = textPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(36.dp), contentAlignment = Alignment.Center) {
                        Text("删除后，该对话将不可恢复，确认删除？", fontSize = 12.sp, color = textSecondary, textAlign = TextAlign.Center)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { showDeleteDialogFor = null },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF0F0F0))
                        ) { Text("取消", color = textSecondary, fontSize = 12.sp) }
                        Button(
                            onClick = {
                                val target = showDeleteDialogFor!!
                                val newSessions = sessions.filter { it.id != target.id }
                                onUpdateSessions(newSessions)
                                if (currentSessionId == target.id) {
                                    onSwitchSession(newSessions.firstOrNull()?.id)
                                }
                                showDeleteDialogFor = null
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                        ) { Text("删除", color = Color.White, fontSize = 12.sp) }
                    }
                }
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = Color.White, modifier = Modifier.width(300.dp).fillMaxHeight()) {
                Column(modifier = Modifier.fillMaxSize().imePadding().statusBarsPadding().padding(16.dp)) {
                    Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFF5F5F5), modifier = Modifier.fillMaxWidth().height(36.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp).fillMaxHeight()) {
                            Icon(Icons.Default.Search, contentDescription = null, tint = textSecondary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = TextStyle(fontSize = 12.sp, color = textPrimary),
                                    singleLine = true,
                                    decorationBox = { innerTextField ->
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                                            if (searchQuery.isEmpty()) Text("搜索聊天内容...", color = textSecondary, fontSize = 12.sp)
                                            innerTextField()
                                        }
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    LazyColumn {
                        val filteredSessions = if (searchQuery.isBlank()) {
                            sessions
                        } else {
                            sessions.filter { it.title.contains(searchQuery, ignoreCase = true) }
                        }

                        val sortedSessions = filteredSessions.sortedWith(compareByDescending<ChatSession> { it.isPinned }.thenByDescending { it.timestamp })

                        val grouped = sortedSessions.groupBy {
                            val diff = System.currentTimeMillis() - it.timestamp
                            when {
                                diff < 24 * 60 * 60 * 1000 -> "今天"
                                diff < 48 * 60 * 60 * 1000 -> "昨天"
                                diff < 7 * 24 * 60 * 60 * 1000 -> "7天内"
                                else -> {
                                    val sdf = SimpleDateFormat("yyyy年M月d日", Locale.getDefault())
                                    sdf.format(Date(it.timestamp))
                                }
                            }
                        }

                        for ((groupName, groupSessions) in grouped) {
                            item { Text(groupName, color = textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)) }

                            items(groupSessions) { session ->
                                var showMenu by remember { mutableStateOf(false) }
                                var isPopupVisible by remember { mutableStateOf(false) }
                                if (showMenu) { isPopupVisible = true }

                                Box {
                                    Text(
                                        text = (if (session.isPinned) "[置顶] " else "") + session.title,
                                        color = if (session.id == currentSessionId) Color(0xFF1A73E8) else textPrimary,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .combinedClickable(
                                                onClick = {
                                                    onSwitchSession(session.id)
                                                    scope.launch { drawerState.close() }
                                                },
                                                onLongClick = { showMenu = true }
                                            )
                                            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp)
                                    )

                                    if (isPopupVisible) {
                                        val transition = updateTransition(targetState = showMenu, label = "menu")
                                        val scale by transition.animateFloat(transitionSpec = { tween(150) }, label = "scale") { if (it) 1f else 0.8f }
                                        val alpha by transition.animateFloat(transitionSpec = { tween(150) }, label = "alpha") { if (it) 1f else 0f }

                                        LaunchedEffect(showMenu) {
                                            if (!showMenu) {
                                                delay(150)
                                                isPopupVisible = false
                                            }
                                        }

                                        Popup(
                                            onDismissRequest = { showMenu = false },
                                            properties = PopupProperties(focusable = true),
                                            offset = IntOffset(x = 170.dp.value.roundToInt(), y = 0)
                                        ) {
                                            Box(
                                                modifier = Modifier.graphicsLayer {
                                                    scaleX = scale
                                                    scaleY = scale
                                                    this.alpha = alpha
                                                }
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .width(100.dp)
                                                        .drawBehind {
                                                            val paint = Paint().apply {
                                                                color = android.graphics.Color.TRANSPARENT
                                                                setShadowLayer(
                                                                    12.dp.toPx(),
                                                                    4.dp.toPx(),
                                                                    4.dp.toPx(),
                                                                    android.graphics.Color.argb(40, 0, 0, 0)
                                                                )
                                                            }
                                                            drawIntoCanvas {
                                                                it.nativeCanvas.drawRoundRect(
                                                                    0f, 0f, size.width, size.height,
                                                                    8.dp.toPx(), 8.dp.toPx(), paint
                                                                )
                                                            }
                                                        }
                                                        .background(Color.White, RoundedCornerShape(8.dp))
                                                ) {
                                                    Column {
                                                        Text("重命名", fontSize = 12.sp, modifier = Modifier.fillMaxWidth().clickable {
                                                            renameInput = session.title
                                                            showRenameDialogFor = session
                                                            showMenu = false
                                                        }.padding(horizontal = 12.dp, vertical = 8.dp))

                                                        Text(if (session.isPinned) "取消置顶" else "置顶", fontSize = 12.sp, modifier = Modifier.fillMaxWidth().clickable {
                                                            session.isPinned = !session.isPinned
                                                            onUpdateSessions(sessions.toList())
                                                            showMenu = false
                                                        }.padding(horizontal = 12.dp, vertical = 8.dp))

                                                        Text("删除", fontSize = 12.sp, color = Color.Red, modifier = Modifier.fillMaxWidth().clickable {
                                                            showDeleteDialogFor = session
                                                            showMenu = false
                                                        }.padding(horizontal = 12.dp, vertical = 8.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) {
        // 【绝对完美混合架构】Column + Box + 兜底白板，上中下三面全部锁死
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .pointerInput(isDrawerOpen) {
                    detectTapGestures(onTap = {
                        if (isDrawerOpen) {
                            // 瞬间砸下键盘
                            focusManager.clearFocus(force = true)
                            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                            imm.hideSoftInputFromWindow(View(context).windowToken, 0)
                            scope.launch { drawerState.close() }
                        } else {
                            focusManager.clearFocus()
                        }
                    })
                }
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 1. 顶部物理隔断，彻底杜绝顶部穿透
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            currentTitle,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = textPrimary
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "菜单", tint = textPrimary)
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { navController.navigate("setup") },
                            modifier = Modifier.padding(end = 8.dp).size(36.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "新建", tint = textPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.White),
                    modifier = Modifier.statusBarsPadding()
                )

                // 2. 中间和底部混合区
                Box(modifier = Modifier.weight(1f)) {

                    val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

                    // 消息列表（铺满下层）
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        // 【核心修改点1】把 bottom 的 4.dp 改成 20.dp，让穿透界限往上移
                        contentPadding = PaddingValues(
                            top = 16.dp,
                            start = 16.dp,
                            end = 16.dp,
                            bottom = navBarHeight + 18.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(messages) { msg ->
                            if (msg.role == "user") {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    Surface(
                                        color = userBubbleColor,
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.widthIn(max = 280.dp)
                                    ) {
                                        Text(
                                            text = msg.content,
                                            modifier = Modifier.padding(12.dp),
                                            color = textPrimary,
                                            fontSize = 12.sp,
                                            lineHeight = 18.sp
                                        )
                                    }
                                }
                            } else {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("已思考 (用时 2 秒)", color = textSecondary, fontSize = 12.sp)
                                        Text(" ∨", color = textSecondary, fontSize = 12.sp)
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(text = msg.content, color = textPrimary, fontSize = 12.sp, lineHeight = 18.sp)
                                }
                            }
                        }
                        if (isLoading) {
                            item { Text("AI 正在思考...", color = textSecondary, fontSize = 12.sp) }
                        }
                    }

                    // 【核心修改点2】底部纯白盾牌高度从 +8 变成 +20，配合列表把底线抬高
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(navBarHeight + 20.dp)
                            .background(Color.White)
                    )

                    // 悬浮输入框
                    if (!isDrawerOpen) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .then(if (isFocused) Modifier.imePadding() else Modifier)
                                .padding(horizontal = 16.dp)
                                .padding(bottom = navBarHeight + 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer { clip = false }
                                    .drawBehind {
                                        val paint = Paint().apply {
                                            color = android.graphics.Color.TRANSPARENT
                                            setShadowLayer(
                                                24.dp.toPx(),
                                                0f,
                                                16.dp.toPx(),
                                                android.graphics.Color.argb(60, 0, 0, 0)
                                            )
                                        }
                                        drawIntoCanvas {
                                            it.nativeCanvas.drawRoundRect(
                                                0f, 0f, size.width, size.height,
                                                24.dp.toPx(), 24.dp.toPx(), paint
                                            )
                                        }
                                    }
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(24.dp),
                                    color = Color.White,
                                    border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                // 【修改点1】高度增加到 14.dp
                                                .padding(horizontal = 16.dp, vertical = 14.dp)
                                                .animateContentSize()
                                        ) {
                                            BasicTextField(
                                                value = input,
                                                onValueChange = { input = it },
                                                interactionSource = interactionSource,
                                                modifier = Modifier.fillMaxWidth(),
                                                textStyle = TextStyle(
                                                    fontSize = 12.sp,
                                                    color = textPrimary,
                                                    lineHeight = 16.sp
                                                ),
                                                maxLines = 5,
                                                decorationBox = { innerTextField ->
                                                    if (input.isEmpty()) {
                                                        Text(
                                                            "在此处发消息。深度思考已开",
                                                            color = placeholderColor,
                                                            fontSize = 12.sp,
                                                            lineHeight = 16.sp
                                                        )
                                                    }
                                                    innerTextField()
                                                }
                                            )
                                        }

                                        Row(
                                            // 【修改点2】增加 offset(y = -4.dp) 让整行按钮上移
                                            modifier = Modifier.fillMaxWidth()
                                                .padding(start = 12.dp, end = 8.dp, bottom = 4.dp)
                                                .offset(y = (-4).dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            Surface(
                                                // 【修改点3】圆角统一改为 24.dp
                                                shape = RoundedCornerShape(24.dp),
                                                color = if (isSearchEnabled) Color(0xFFE8F0FE) else Color.Transparent,
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (isSearchEnabled) Color(0xFF1A73E8) else Color(0xFFDDDDDD)
                                                ),
                                                modifier = Modifier.clip(RoundedCornerShape(24.dp)).clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null
                                                ) { isSearchEnabled = !isSearchEnabled }
                                            ) {
                                                // 因为圆角变大了，水平 padding 也稍微给大一点点，视觉才协调
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 0.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        "智能搜索",
                                                        color = if (isSearchEnabled) Color(0xFF1A73E8) else textPrimary,
                                                        fontSize = 10.sp
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))

                                            Icon(
                                                Icons.Default.Add, contentDescription = "附件",
                                                tint = textPrimary,
                                                modifier = Modifier.size(18.dp).clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null
                                                ) { }
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))

                                            if (input.isNotBlank()) {
                                                Button(
                                                    onClick = {
                                                        if (isLoading || currentSession == null) return@Button
                                                        val userMsg = Message("user", input)
                                                        val newMsgs = messages + userMsg
                                                        val updatedSession = currentSession.copy(
                                                            messages = newMsgs.toMutableList(),
                                                            timestamp = System.currentTimeMillis()
                                                        )
                                                        val newSessions =
                                                            sessions.map { if (it.id == currentSession.id) updatedSession else it }
                                                        onUpdateSessions(newSessions)

                                                        val promptToSend = input
                                                        input = ""
                                                        isLoading = true
                                                        focusManager.clearFocus()

                                                        scope.launch {
                                                            if (globalApiKey.isBlank()) {
                                                                val errMsgs = newMsgs + Message(
                                                                    "assistant",
                                                                    "请先去新建对话里填入 API Key！"
                                                                )
                                                                onUpdateSessions(newSessions.map {
                                                                    if (it.id == currentSession.id) updatedSession.copy(
                                                                        messages = errMsgs.toMutableList()
                                                                    ) else it
                                                                })
                                                                isLoading = false
                                                                return@launch
                                                            }

                                                            val reply = ApiClient.chat(
                                                                globalBaseUrl,
                                                                globalApiKey,
                                                                currentSession.model,
                                                                promptToSend
                                                            )
                                                            val finalMsgs = newMsgs + Message("assistant", reply)
                                                            onUpdateSessions(newSessions.map {
                                                                if (it.id == currentSession.id) updatedSession.copy(
                                                                    messages = finalMsgs.toMutableList()
                                                                ) else it
                                                            })
                                                            isLoading = false
                                                        }
                                                    },
                                                    modifier = Modifier.size(24.dp),
                                                    contentPadding = PaddingValues(0.dp),
                                                    shape = CircleShape,
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = Color(
                                                            0xFF1A73E8
                                                        )
                                                    )
                                                ) {
                                                    Text(
                                                        "↑",
                                                        color = Color.White,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
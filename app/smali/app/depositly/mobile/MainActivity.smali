.class public Lapp/depositly/mobile/MainActivity;
.super Landroid/app/Activity;
.source "MainActivity.java"


# annotations
.annotation system Ldalvik/annotation/MemberClasses;
    value = {
        Lapp/depositly/mobile/MainActivity$Store;
    }
.end annotation


# instance fields
.field private pageReady:Z

.field private pendingImport:Ljava/lang/String;

.field private web:Landroid/webkit/WebView;


# direct methods
.method public constructor <init>()V
    .locals 1

    .line 11
    invoke-direct {p0}, Landroid/app/Activity;-><init>()V

    .line 14
    const/4 v0, 0x0

    iput-boolean v0, p0, Lapp/depositly/mobile/MainActivity;->pageReady:Z

    return-void
.end method

.method static synthetic access$002(Lapp/depositly/mobile/MainActivity;Z)Z
    .locals 0

    .line 11
    iput-boolean p1, p0, Lapp/depositly/mobile/MainActivity;->pageReady:Z

    return p1
.end method

.method static synthetic access$100(Lapp/depositly/mobile/MainActivity;)V
    .locals 0

    .line 11
    invoke-direct {p0}, Lapp/depositly/mobile/MainActivity;->deliverImport()V

    return-void
.end method

.method static synthetic access$200(Lapp/depositly/mobile/MainActivity;)Ljava/io/File;
    .locals 0

    .line 11
    invoke-direct {p0}, Lapp/depositly/mobile/MainActivity;->exportCache()Ljava/io/File;

    move-result-object p0

    return-object p0
.end method

.method static synthetic access$300(Lapp/depositly/mobile/MainActivity;Ljava/lang/String;)V
    .locals 0

    .line 11
    invoke-direct {p0, p1}, Lapp/depositly/mobile/MainActivity;->message(Ljava/lang/String;)V

    return-void
.end method

.method private deliverImport()V
    .locals 5

    .line 36
    iget-boolean v0, p0, Lapp/depositly/mobile/MainActivity;->pageReady:Z

    if-eqz v0, :cond_0

    iget-object v0, p0, Lapp/depositly/mobile/MainActivity;->pendingImport:Ljava/lang/String;

    if-eqz v0, :cond_0

    const/4 v1, 0x0

    iput-object v1, p0, Lapp/depositly/mobile/MainActivity;->pendingImport:Ljava/lang/String;

    iget-object v2, p0, Lapp/depositly/mobile/MainActivity;->web:Landroid/webkit/WebView;

    new-instance v3, Ljava/lang/StringBuilder;

    invoke-direct {v3}, Ljava/lang/StringBuilder;-><init>()V

    const-string v4, "restoreData("

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v3

    invoke-static {v0}, Lorg/json/JSONObject;->quote(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v0

    invoke-virtual {v3, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v0

    const-string v3, ")"

    invoke-virtual {v0, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v0

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    invoke-virtual {v2, v0, v1}, Landroid/webkit/WebView;->evaluateJavascript(Ljava/lang/String;Landroid/webkit/ValueCallback;)V

    :cond_0
    return-void
.end method

.method private exportCache()Ljava/io/File;
    .locals 3

    .line 35
    new-instance v0, Ljava/io/File;

    invoke-virtual {p0}, Lapp/depositly/mobile/MainActivity;->getCacheDir()Ljava/io/File;

    move-result-object v1

    const-string v2, "pending-export.txt"

    invoke-direct {v0, v1, v2}, Ljava/io/File;-><init>(Ljava/io/File;Ljava/lang/String;)V

    return-object v0
.end method

.method private message(Ljava/lang/String;)V
    .locals 1

    .line 34
    new-instance v0, Lapp/depositly/mobile/MainActivity$$ExternalSyntheticLambda0;

    invoke-direct {v0, p0, p1}, Lapp/depositly/mobile/MainActivity$$ExternalSyntheticLambda0;-><init>(Lapp/depositly/mobile/MainActivity;Ljava/lang/String;)V

    invoke-virtual {p0, v0}, Lapp/depositly/mobile/MainActivity;->runOnUiThread(Ljava/lang/Runnable;)V

    return-void
.end method


# virtual methods
.method synthetic lambda$message$0$bd-fdr-vault-MainActivity(Ljava/lang/String;)V
    .locals 3

    .line 34
    iget-object v0, p0, Lapp/depositly/mobile/MainActivity;->web:Landroid/webkit/WebView;

    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "toast("

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v1

    invoke-static {p1}, Lorg/json/JSONObject;->quote(Ljava/lang/String;)Ljava/lang/String;

    move-result-object p1

    invoke-virtual {v1, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p1

    const-string v1, ")"

    invoke-virtual {p1, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p1

    invoke-virtual {p1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p1

    const/4 v1, 0x0

    invoke-virtual {v0, p1, v1}, Landroid/webkit/WebView;->evaluateJavascript(Ljava/lang/String;Landroid/webkit/ValueCallback;)V

    return-void
.end method

.method protected onActivityResult(IILandroid/content/Intent;)V
    .locals 6

    .line 44
    invoke-super {p0, p1, p2, p3}, Landroid/app/Activity;->onActivityResult(IILandroid/content/Intent;)V

    const/4 v0, -0x1

    if-ne p2, v0, :cond_a

    if-nez p3, :cond_0

    goto/16 :goto_8

    .line 45
    :cond_0
    :try_start_0
    invoke-virtual {p3}, Landroid/content/Intent;->getData()Landroid/net/Uri;

    move-result-object p2

    .line 46
    const/16 p3, 0xa

    const/4 v1, 0x0

    const/16 v2, 0x1000

    if-ne p1, p3, :cond_4

    new-instance p3, Ljava/io/FileInputStream;

    invoke-direct {p0}, Lapp/depositly/mobile/MainActivity;->exportCache()Ljava/io/File;

    move-result-object v3

    invoke-direct {p3, v3}, Ljava/io/FileInputStream;-><init>(Ljava/io/File;)V
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    :try_start_1
    invoke-virtual {p0}, Lapp/depositly/mobile/MainActivity;->getContentResolver()Landroid/content/ContentResolver;

    move-result-object v3

    invoke-virtual {v3, p2}, Landroid/content/ContentResolver;->openOutputStream(Landroid/net/Uri;)Ljava/io/OutputStream;

    move-result-object v3
    :try_end_1
    .catchall {:try_start_1 .. :try_end_1} :catchall_2

    :try_start_2
    new-array v4, v2, [B

    :goto_0
    invoke-virtual {p3, v4}, Ljava/io/InputStream;->read([B)I

    move-result v5

    if-eq v5, v0, :cond_1

    invoke-virtual {v3, v4, v1, v5}, Ljava/io/OutputStream;->write([BII)V
    :try_end_2
    .catchall {:try_start_2 .. :try_end_2} :catchall_0

    goto :goto_0

    :cond_1
    if-eqz v3, :cond_2

    :try_start_3
    invoke-virtual {v3}, Ljava/io/OutputStream;->close()V
    :try_end_3
    .catchall {:try_start_3 .. :try_end_3} :catchall_2

    :cond_2
    :try_start_4
    invoke-virtual {p3}, Ljava/io/InputStream;->close()V

    invoke-direct {p0}, Lapp/depositly/mobile/MainActivity;->exportCache()Ljava/io/File;

    move-result-object p3

    invoke-virtual {p3}, Ljava/io/File;->delete()Z

    const-string p3, "Export saved"

    invoke-direct {p0, p3}, Lapp/depositly/mobile/MainActivity;->message(Ljava/lang/String;)V
    :try_end_4
    .catch Ljava/lang/Exception; {:try_start_4 .. :try_end_4} :catch_0

    goto :goto_3

    :catchall_0
    move-exception p1

    if-eqz v3, :cond_3

    :try_start_5
    invoke-virtual {v3}, Ljava/io/OutputStream;->close()V
    :try_end_5
    .catchall {:try_start_5 .. :try_end_5} :catchall_1

    goto :goto_1

    :catchall_1
    move-exception p2

    :try_start_6
    invoke-virtual {p1, p2}, Ljava/lang/Throwable;->addSuppressed(Ljava/lang/Throwable;)V

    :cond_3
    :goto_1
    throw p1
    :try_end_6
    .catchall {:try_start_6 .. :try_end_6} :catchall_2

    :catchall_2
    move-exception p1

    :try_start_7
    invoke-virtual {p3}, Ljava/io/InputStream;->close()V
    :try_end_7
    .catchall {:try_start_7 .. :try_end_7} :catchall_3

    goto :goto_2

    :catchall_3
    move-exception p2

    :try_start_8
    invoke-virtual {p1, p2}, Ljava/lang/Throwable;->addSuppressed(Ljava/lang/Throwable;)V

    :goto_2
    throw p1

    .line 47
    :cond_4
    :goto_3
    const/16 p3, 0xb

    if-ne p1, p3, :cond_9

    invoke-virtual {p0}, Lapp/depositly/mobile/MainActivity;->getContentResolver()Landroid/content/ContentResolver;

    move-result-object p1

    invoke-virtual {p1, p2}, Landroid/content/ContentResolver;->openInputStream(Landroid/net/Uri;)Ljava/io/InputStream;

    move-result-object p1
    :try_end_8
    .catch Ljava/lang/Exception; {:try_start_8 .. :try_end_8} :catch_0

    :try_start_9
    new-instance p2, Ljava/io/ByteArrayOutputStream;

    invoke-direct {p2}, Ljava/io/ByteArrayOutputStream;-><init>()V

    new-array p3, v2, [B

    :goto_4
    invoke-virtual {p1, p3}, Ljava/io/InputStream;->read([B)I

    move-result v2

    if-eq v2, v0, :cond_6

    invoke-virtual {p2, p3, v1, v2}, Ljava/io/ByteArrayOutputStream;->write([BII)V

    invoke-virtual {p2}, Ljava/io/ByteArrayOutputStream;->size()I

    move-result v2

    const v3, 0x1312d00

    if-gt v2, v3, :cond_5

    goto :goto_4

    :cond_5
    new-instance p2, Ljava/io/IOException;

    const-string p3, "Backup too large"

    invoke-direct {p2, p3}, Ljava/io/IOException;-><init>(Ljava/lang/String;)V

    throw p2

    :cond_6
    const-string p3, "UTF-8"

    invoke-virtual {p2, p3}, Ljava/io/ByteArrayOutputStream;->toString(Ljava/lang/String;)Ljava/lang/String;

    move-result-object p2
    :try_end_9
    .catchall {:try_start_9 .. :try_end_9} :catchall_4

    if-eqz p1, :cond_7

    :try_start_a
    invoke-virtual {p1}, Ljava/io/InputStream;->close()V

    :cond_7
    iput-object p2, p0, Lapp/depositly/mobile/MainActivity;->pendingImport:Ljava/lang/String;

    invoke-direct {p0}, Lapp/depositly/mobile/MainActivity;->deliverImport()V
    :try_end_a
    .catch Ljava/lang/Exception; {:try_start_a .. :try_end_a} :catch_0

    goto :goto_6

    :catchall_4
    move-exception p2

    if-eqz p1, :cond_8

    :try_start_b
    invoke-virtual {p1}, Ljava/io/InputStream;->close()V
    :try_end_b
    .catchall {:try_start_b .. :try_end_b} :catchall_5

    goto :goto_5

    :catchall_5
    move-exception p1

    :try_start_c
    invoke-virtual {p2, p1}, Ljava/lang/Throwable;->addSuppressed(Ljava/lang/Throwable;)V

    :cond_8
    :goto_5
    throw p2
    :try_end_c
    .catch Ljava/lang/Exception; {:try_start_c .. :try_end_c} :catch_0

    .line 48
    :cond_9
    :goto_6
    goto :goto_7

    :catch_0
    move-exception p1

    const-string p1, "Could not read or save the file. Please try again."

    invoke-direct {p0, p1}, Lapp/depositly/mobile/MainActivity;->message(Ljava/lang/String;)V

    :goto_7
    return-void

    .line 44
    :cond_a
    :goto_8
    return-void
.end method

.method public onBackPressed()V
    .locals 3

    .line 49
    iget-object v0, p0, Lapp/depositly/mobile/MainActivity;->web:Landroid/webkit/WebView;

    const-string v1, "handleBack()"

    const/4 v2, 0x0

    invoke-virtual {v0, v1, v2}, Landroid/webkit/WebView;->evaluateJavascript(Ljava/lang/String;Landroid/webkit/ValueCallback;)V

    return-void
.end method

.method public onCreate(Landroid/os/Bundle;)V
    .locals 6

    .line 16
    invoke-super {p0, p1}, Landroid/app/Activity;->onCreate(Landroid/os/Bundle;)V

    .line 18
    new-instance p1, Landroid/webkit/WebView;

    invoke-direct {p1, p0}, Landroid/webkit/WebView;-><init>(Landroid/content/Context;)V

    iput-object p1, p0, Lapp/depositly/mobile/MainActivity;->web:Landroid/webkit/WebView;

    invoke-virtual {p0, p1}, Lapp/depositly/mobile/MainActivity;->setContentView(Landroid/view/View;)V

    # Apply saved appearance before loading content, so relaunches retain dark bars.
    invoke-direct {p0}, Lapp/depositly/mobile/MainActivity;->restoreSystemTheme()V

    .line 20
    iget-object p1, p0, Lapp/depositly/mobile/MainActivity;->web:Landroid/webkit/WebView;

    invoke-virtual {p1}, Landroid/webkit/WebView;->getSettings()Landroid/webkit/WebSettings;

    move-result-object p1

    const/4 v0, 0x1

    invoke-virtual {p1, v0}, Landroid/webkit/WebSettings;->setJavaScriptEnabled(Z)V

    .line 21
    iget-object p1, p0, Lapp/depositly/mobile/MainActivity;->web:Landroid/webkit/WebView;

    invoke-virtual {p1}, Landroid/webkit/WebView;->getSettings()Landroid/webkit/WebSettings;

    move-result-object p1

    const/4 v0, 0x0

    invoke-virtual {p1, v0}, Landroid/webkit/WebSettings;->setAllowFileAccess(Z)V

    iget-object p1, p0, Lapp/depositly/mobile/MainActivity;->web:Landroid/webkit/WebView;

    invoke-virtual {p1}, Landroid/webkit/WebView;->getSettings()Landroid/webkit/WebSettings;

    move-result-object p1

    invoke-virtual {p1, v0}, Landroid/webkit/WebSettings;->setAllowContentAccess(Z)V

    .line 22
    iget-object p1, p0, Lapp/depositly/mobile/MainActivity;->web:Landroid/webkit/WebView;

    new-instance v1, Lapp/depositly/mobile/MainActivity$1;

    invoke-direct {v1, p0}, Lapp/depositly/mobile/MainActivity$1;-><init>(Lapp/depositly/mobile/MainActivity;)V

    invoke-virtual {p1, v1}, Landroid/webkit/WebView;->setWebViewClient(Landroid/webkit/WebViewClient;)V

    .line 26
    iget-object p1, p0, Lapp/depositly/mobile/MainActivity;->web:Landroid/webkit/WebView;

    new-instance v1, Lapp/depositly/mobile/MainActivity$2;

    invoke-direct {v1, p0}, Lapp/depositly/mobile/MainActivity$2;-><init>(Lapp/depositly/mobile/MainActivity;)V

    invoke-virtual {p1, v1}, Landroid/webkit/WebView;->setWebChromeClient(Landroid/webkit/WebChromeClient;)V

    .line 29
    iget-object p1, p0, Lapp/depositly/mobile/MainActivity;->web:Landroid/webkit/WebView;

    new-instance v1, Lapp/depositly/mobile/MainActivity$Store;

    invoke-direct {v1, p0}, Lapp/depositly/mobile/MainActivity$Store;-><init>(Lapp/depositly/mobile/MainActivity;)V

    const-string v2, "Vault"

    invoke-virtual {p1, v1, v2}, Landroid/webkit/WebView;->addJavascriptInterface(Ljava/lang/Object;Ljava/lang/String;)V

    new-instance v1, Lapp/depositly/mobile/reminders/ReminderBridge;
    invoke-direct {v1, p0, p1}, Lapp/depositly/mobile/reminders/ReminderBridge;-><init>(Landroid/app/Activity;Landroid/webkit/WebView;)V
    const-string v2, "Reminders"
    invoke-virtual {p1, v1, v2}, Landroid/webkit/WebView;->addJavascriptInterface(Ljava/lang/Object;Ljava/lang/String;)V


    .line 30
    :try_start_0
    new-instance p1, Ljava/io/ByteArrayOutputStream;

    invoke-direct {p1}, Ljava/io/ByteArrayOutputStream;-><init>()V

    invoke-virtual {p0}, Lapp/depositly/mobile/MainActivity;->getAssets()Landroid/content/res/AssetManager;

    move-result-object v1

    const-string v2, "index.html"

    invoke-virtual {v1, v2}, Landroid/content/res/AssetManager;->open(Ljava/lang/String;)Ljava/io/InputStream;

    move-result-object v1
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    const/16 v2, 0x1000

    :try_start_1
    new-array v2, v2, [B

    :goto_0
    invoke-virtual {v1, v2}, Ljava/io/InputStream;->read([B)I

    move-result v3

    const/4 v4, -0x1

    if-eq v3, v4, :cond_0

    invoke-virtual {p1, v2, v0, v3}, Ljava/io/ByteArrayOutputStream;->write([BII)V
    :try_end_1
    .catchall {:try_start_1 .. :try_end_1} :catchall_0

    goto :goto_0

    :cond_0
    if-eqz v1, :cond_1

    :try_start_2
    invoke-virtual {v1}, Ljava/io/InputStream;->close()V

    :cond_1
    new-instance v2, Ljava/lang/String;

    invoke-virtual {p1}, Ljava/io/ByteArrayOutputStream;->toByteArray()[B

    move-result-object p1

    sget-object v0, Ljava/nio/charset/StandardCharsets;->UTF_8:Ljava/nio/charset/Charset;

    invoke-direct {v2, p1, v0}, Ljava/lang/String;-><init>([BLjava/nio/charset/Charset;)V

    .line 31
    iget-object v0, p0, Lapp/depositly/mobile/MainActivity;->web:Landroid/webkit/WebView;

    const-string v1, "https://depositly.local/"

    const-string v3, "text/html"

    const-string v4, "UTF-8"

    const/4 v5, 0x0

    invoke-virtual/range {v0 .. v5}, Landroid/webkit/WebView;->loadDataWithBaseURL(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V
    :try_end_2
    .catch Ljava/lang/Exception; {:try_start_2 .. :try_end_2} :catch_0

    .line 32
    nop

    .line 33
    return-void

    .line 30
    :catchall_0
    move-exception p1

    if-eqz v1, :cond_2

    :try_start_3
    invoke-virtual {v1}, Ljava/io/InputStream;->close()V
    :try_end_3
    .catchall {:try_start_3 .. :try_end_3} :catchall_1

    goto :goto_1

    :catchall_1
    move-exception v0

    :try_start_4
    invoke-virtual {p1, v0}, Ljava/lang/Throwable;->addSuppressed(Ljava/lang/Throwable;)V

    :cond_2
    :goto_1
    throw p1
    :try_end_4
    .catch Ljava/lang/Exception; {:try_start_4 .. :try_end_4} :catch_0

    .line 32
    :catch_0
    move-exception p1

    new-instance v0, Ljava/lang/RuntimeException;

    invoke-direct {v0, p1}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/Throwable;)V

    throw v0
.end method

# Called on the main thread at startup and when the app's theme changes.
.method public applySystemTheme(Z)V
    .locals 5

    invoke-virtual {p0}, Lapp/depositly/mobile/MainActivity;->getWindow()Landroid/view/Window;
    move-result-object v0
    const v1, 0xfff6f7f3
    if-eqz p1, :color_ready
    const v1, 0xff101b18
    :color_ready
    invoke-virtual {v0, v1}, Landroid/view/Window;->setStatusBarColor(I)V
    invoke-virtual {v0, v1}, Landroid/view/Window;->setNavigationBarColor(I)V
    invoke-virtual {v0}, Landroid/view/Window;->getDecorView()Landroid/view/View;
    move-result-object v2
    invoke-virtual {v2, v1}, Landroid/view/View;->setBackgroundColor(I)V

    # Clear only LIGHT_STATUS_BAR and LIGHT_NAVIGATION_BAR; retain all other flags.
    invoke-virtual {v2}, Landroid/view/View;->getSystemUiVisibility()I
    move-result v3
    const v4, -0x2011
    and-int/2addr v3, v4
    if-nez p1, :flags_ready
    or-int/lit16 v3, v3, 0x2010
    :flags_ready
    invoke-virtual {v2, v3}, Landroid/view/View;->setSystemUiVisibility(I)V
    iget-object v2, p0, Lapp/depositly/mobile/MainActivity;->web:Landroid/webkit/WebView;
    if-eqz v2, :web_ready
    invoke-virtual {v2, v1}, Landroid/webkit/WebView;->setBackgroundColor(I)V
    invoke-virtual {v2}, Landroid/webkit/WebView;->getSystemUiVisibility()I
    move-result v3
    and-int/2addr v3, v4
    if-nez p1, :web_flags_ready
    or-int/lit16 v3, v3, 0x2010
    :web_flags_ready
    invoke-virtual {v2, v3}, Landroid/webkit/WebView;->setSystemUiVisibility(I)V
    :web_ready

    # Android 10+: avoid contrast scrims over the explicitly themed bar colors.
    sget v1, Landroid/os/Build$VERSION;->SDK_INT:I
    const/16 v2, 0x1d
    if-lt v1, v2, :done
    const/4 v1, 0x0
    invoke-virtual {v0, v1}, Landroid/view/Window;->setStatusBarContrastEnforced(Z)V
    invoke-virtual {v0, v1}, Landroid/view/Window;->setNavigationBarContrastEnforced(Z)V
    :done
    return-void
.end method

.method private restoreSystemTheme()V
    .locals 4

    const/4 v0, 0x0
    :try_start_theme
    const-string v1, "vault"
    invoke-virtual {p0, v1, v0}, Lapp/depositly/mobile/MainActivity;->getSharedPreferences(Ljava/lang/String;I)Landroid/content/SharedPreferences;
    move-result-object v1
    const-string v2, "data"
    const-string v3, "{}"
    invoke-interface {v1, v2, v3}, Landroid/content/SharedPreferences;->getString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
    move-result-object v1
    new-instance v2, Lorg/json/JSONObject;
    invoke-direct {v2, v1}, Lorg/json/JSONObject;-><init>(Ljava/lang/String;)V
    const-string v1, "dark"
    invoke-virtual {v2, v1, v0}, Lorg/json/JSONObject;->optBoolean(Ljava/lang/String;Z)Z
    move-result v0
    :try_end_theme
    .catch Ljava/lang/Exception; {:try_start_theme .. :try_end_theme} :catch_theme
    goto :theme_loaded
    :catch_theme
    move-exception v1
    :theme_loaded
    invoke-virtual {p0, v0}, Lapp/depositly/mobile/MainActivity;->applySystemTheme(Z)V
    return-void
.end method

.method public reminderPageReady()V
    .locals 1
    iget-object v0, p0, Lapp/depositly/mobile/MainActivity;->web:Landroid/webkit/WebView;
    invoke-static {p0, v0}, Lapp/depositly/mobile/reminders/ReminderBridge;->pageReady(Landroid/app/Activity;Landroid/webkit/WebView;)V
    return-void
.end method

.method protected onResume()V
    .locals 1
    invoke-super {p0}, Landroid/app/Activity;->onResume()V
    iget-object v0, p0, Lapp/depositly/mobile/MainActivity;->web:Landroid/webkit/WebView;
    invoke-static {p0, v0}, Lapp/depositly/mobile/reminders/ReminderBridge;->onResume(Landroid/app/Activity;Landroid/webkit/WebView;)V
    return-void
.end method

.method public onRequestPermissionsResult(I[Ljava/lang/String;[I)V
    .locals 1
    invoke-super {p0, p1, p2, p3}, Landroid/app/Activity;->onRequestPermissionsResult(I[Ljava/lang/String;[I)V
    const/16 v0, 0x1007
    if-ne p1, v0, :permission_done
    iget-object v0, p0, Lapp/depositly/mobile/MainActivity;->web:Landroid/webkit/WebView;
    invoke-static {p0, v0}, Lapp/depositly/mobile/reminders/ReminderBridge;->permissionResult(Landroid/app/Activity;Landroid/webkit/WebView;)V
    :permission_done
    return-void
.end method

.method protected onNewIntent(Landroid/content/Intent;)V
    .locals 1
    invoke-super {p0, p1}, Landroid/app/Activity;->onNewIntent(Landroid/content/Intent;)V
    invoke-virtual {p0, p1}, Lapp/depositly/mobile/MainActivity;->setIntent(Landroid/content/Intent;)V
    iget-boolean v0, p0, Lapp/depositly/mobile/MainActivity;->pageReady:Z
    if-eqz v0, :intent_done
    invoke-virtual {p0}, Lapp/depositly/mobile/MainActivity;->reminderPageReady()V
    :intent_done
    return-void
.end method

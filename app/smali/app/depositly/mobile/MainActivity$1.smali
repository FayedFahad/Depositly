.class Lapp/depositly/mobile/MainActivity$1;
.super Landroid/webkit/WebViewClient;
.source "MainActivity.java"


# annotations
.annotation system Ldalvik/annotation/EnclosingMethod;
    value = Lapp/depositly/mobile/MainActivity;->onCreate(Landroid/os/Bundle;)V
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x0
    name = null
.end annotation


# instance fields
.field final synthetic this$0:Lapp/depositly/mobile/MainActivity;


# direct methods
.method constructor <init>(Lapp/depositly/mobile/MainActivity;)V
    .locals 0

    .line 22
    iput-object p1, p0, Lapp/depositly/mobile/MainActivity$1;->this$0:Lapp/depositly/mobile/MainActivity;

    invoke-direct {p0}, Landroid/webkit/WebViewClient;-><init>()V

    return-void
.end method


# virtual methods
.method public onPageFinished(Landroid/webkit/WebView;Ljava/lang/String;)V
    .locals 0

    .line 24
    iget-object p1, p0, Lapp/depositly/mobile/MainActivity$1;->this$0:Lapp/depositly/mobile/MainActivity;

    const/4 p2, 0x1

    invoke-static {p1, p2}, Lapp/depositly/mobile/MainActivity;->access$002(Lapp/depositly/mobile/MainActivity;Z)Z

    iget-object p1, p0, Lapp/depositly/mobile/MainActivity$1;->this$0:Lapp/depositly/mobile/MainActivity;

    invoke-static {p1}, Lapp/depositly/mobile/MainActivity;->access$100(Lapp/depositly/mobile/MainActivity;)V

    invoke-virtual {p1}, Lapp/depositly/mobile/MainActivity;->reminderPageReady()V

    return-void
.end method

.method public shouldOverrideUrlLoading(Landroid/webkit/WebView;Landroid/webkit/WebResourceRequest;)Z
    .locals 0

    .line 23
    const/4 p1, 0x1

    return p1
.end method

.class final Lapp/depositly/mobile/ThemeUpdate;
.super Ljava/lang/Object;
.implements Ljava/lang/Runnable;

.field private final activity:Lapp/depositly/mobile/MainActivity;
.field private final dark:Z

.method public constructor <init>(Lapp/depositly/mobile/MainActivity;Z)V
    .locals 0
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    iput-object p1, p0, Lapp/depositly/mobile/ThemeUpdate;->activity:Lapp/depositly/mobile/MainActivity;
    iput-boolean p2, p0, Lapp/depositly/mobile/ThemeUpdate;->dark:Z
    return-void
.end method

.method public run()V
    .locals 2
    iget-object v0, p0, Lapp/depositly/mobile/ThemeUpdate;->activity:Lapp/depositly/mobile/MainActivity;
    iget-boolean v1, p0, Lapp/depositly/mobile/ThemeUpdate;->dark:Z
    invoke-virtual {v0, v1}, Lapp/depositly/mobile/MainActivity;->applySystemTheme(Z)V
    return-void
.end method

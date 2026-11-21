# superterm — fish 默认 shell 的环境配置
# 由 App 安装时放到 $PREFIX/etc/config.fish，并通过 XDG_CONFIG_HOME 生效

set -gx DRIVE /data/user/0/com.mucre.superterm/drive
set -gx PREFIX $DRIVE/usr
set -gx PATH $PREFIX/bin /system/bin /system/xbin
set -gx HOME $DRIVE/home
set -gx TMPDIR $PREFIX/tmp
set -gx TERMINFO $PREFIX/share/terminfo
set -gx SHELL $PREFIX/bin/fish
set -q TERM; or set -gx TERM xterm-256color
set -gx LANG en_US.UTF-8

set -l _arch (uname -m 2>/dev/null; or echo unknown)
set -l _rel (getprop ro.build.version.release 2>/dev/null; or echo unknown)
set -l _tag DATA
if command -v su >/dev/null 2>&1
    if su -c id 2>/dev/null | string match -q 'uid=0*'
        set _tag Root
    end
end
set -gx OSDR "Android-$_rel-$_arch-$_tag"

function eggtool --description '鸡蛋工具'
    switch "$argv[1]"
        case help
            echo "暂无公告"
        case by
            echo "版本号为Egg hacker1.0.0(beta)"
        case ls
            echo "您当前目录是/usr/share/eggtool/files/，并且现在有0个文件夹，0个文件"
        case moo
            echo "🐮Hahaha"
        case gtk
            echo "手机架构: "(uname -m)
        case banner logo
            ls
            printf '%s\n' ' ___' '/   | () () |' ' \  ^  /' '  |||||' '  |||||' '鸡蛋工具 EggTool'
        case ''
            echo "用法: eggtool [help|by|ls|moo|gtk|banner]"
        case '*'
            echo "未知命令: $argv[1]"
            echo "用法: eggtool [help|by|ls|moo|gtk|banner]"
    end
end

function fish_prompt
    set_color cyan
    echo -n "$OSDR"
    set_color normal
    echo -n " "
    set_color green
    echo -n (prompt_pwd)
    set_color normal
    echo -n " > "
end

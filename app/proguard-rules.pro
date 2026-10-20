# Copyright (C) 2026  Henrique Almeida <me@h3nc4.com>
#
# This file is part of HyperGesture.
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <https://www.gnu.org/licenses/>.

# Instantiated by the platform from manifest names, so they must survive shrinking.
-keep class com.h3nc4.hypergesture.service.HyperGestureAccessibilityService { *; }
-keep class com.h3nc4.hypergesture.boot.BootCompletedReceiver { *; }

-keep class * extends android.accessibilityservice.AccessibilityService { *; }

# Debug and verbose logs run on every edge touch, so release drops each call and the string
# built for it. A line e2e or a bug report reads logs at info or above.
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}

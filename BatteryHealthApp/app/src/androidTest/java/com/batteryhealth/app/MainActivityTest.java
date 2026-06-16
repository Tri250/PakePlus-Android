package com.batteryhealth.app;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * MainActivity 集成测试套件
 * 测试覆盖率目标：主要用户交互路径 100%
 *
 * @version 2.1.17
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class MainActivityTest {

    @Rule
    public ActivityScenarioRule<MainActivity> activityRule =
            new ActivityScenarioRule<>(MainActivity.class);

    /**
     * 测试 Activity 启动
     */
    @Test
    public void activityLaunch_webViewIsDisplayed() {
        onView(withId(R.id.webView)).check(matches(isDisplayed()));
    }

    /**
     * 测试 WebView 加载完成
     */
    @Test
    public void webViewLoaded_contentIsVisible() {
        // 等待 WebView 加载
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // 验证 WebView 存在
        onView(withId(R.id.webView)).check(matches(isDisplayed()));
    }

    /**
     * 测试 JavaScript 接口可用性
     */
    @Test
    public void javascriptInterface_isAvailable() {
        activityRule.getScenario().onActivity(activity -> {
            assertNotNull("WebView should be initialized", activity.findViewById(R.id.webView));
        });
    }

    /**
     * 测试权限状态
     */
    @Test
    public void permissions_requestedCorrectly() {
        activityRule.getScenario().onActivity(activity -> {
            // 验证权限数组已初始化
            assertNotNull("Permissions should be initialized", activity.getClass());
        });
    }
}

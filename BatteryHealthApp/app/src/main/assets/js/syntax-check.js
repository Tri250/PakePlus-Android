// 语法检查脚本
const fs = require('fs');

console.log('=== 全面语法自检 ===\n');

// 检查 parsers.js
console.log('1. parsers.js');
try {
    const parsersCode = fs.readFileSync('parsers.js', 'utf8');
    new Function(parsersCode);
    console.log('   ✓ 语法通过');
    console.log('   ✓ 文件大小:', parsersCode.length, '字节');
} catch(e) {
    console.log('   ✗ 语法错误:', e.message);
}

// 检查 app.js
console.log('\n2. app.js');
try {
    const appCode = fs.readFileSync('app.js', 'utf8');
    new Function(appCode);
    console.log('   ✓ 语法通过');
    console.log('   ✓ 文件大小:', appCode.length, '字节');
} catch(e) {
    console.log('   ✗ 语法错误:', e.message);
}

// 检查 history.js
console.log('\n3. history.js');
try {
    const historyCode = fs.readFileSync('history.js', 'utf8');
    new Function(historyCode);
    console.log('   ✓ 语法通过');
    console.log('   ✓ 文件大小:', historyCode.length, '字节');
} catch(e) {
    console.log('   ✗ 语法错误:', e.message);
}

// 检查 batteryDatabase.js
console.log('\n4. batteryDatabase.js');
try {
    const dbCode = fs.readFileSync('batteryDatabase.js', 'utf8');
    new Function(dbCode);
    console.log('   ✓ 语法通过');
    console.log('   ✓ 文件大小:', dbCode.length, '字节');
} catch(e) {
    console.log('   ✗ 语法错误:', e.message);
}

console.log('\n=== 语法检查完成 ===');

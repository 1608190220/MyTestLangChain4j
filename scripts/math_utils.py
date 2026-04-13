# -*- coding: utf-8 -*-
"""
数学计算工具脚本
用于复杂数学运算
"""

import math

def factorial(n):
    """计算阶乘"""
    return math.factorial(n)

def fibonacci(n):
    """计算斐波那契数列第 n 项"""
    if n <= 1:
        return n
    a, b = 0, 1
    for _ in range(2, n + 1):
        a, b = b, a + b
    return b

def is_prime(n):
    """判断是否为质数"""
    if n < 2:
        return False
    for i in range(2, int(math.sqrt(n)) + 1):
        if n % i == 0:
            return False
    return True

if __name__ == '__main__':
    print('Math utility script loaded')

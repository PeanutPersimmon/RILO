package io.github.peanutpersimmon.rilo.domain.model

sealed class CategoryOperationException(message: String) : IllegalArgumentException(message)

class BlankCategoryNameException : CategoryOperationException("分类名称不能为空")

class ActiveCategoryNameConflictException(val conflictingId: Long) :
    CategoryOperationException("已有同名分类")

class CategoryNotFoundException(val categoryId: Long) :
    CategoryOperationException("分类不存在")

class InvalidCategoryColorKeyException(val colorKey: String) :
    CategoryOperationException("分类颜色不在预设色板中")

class CategoryOrderChangedException : CategoryOperationException("分类已变化，请重试")

class BlankCategoryKeywordException : CategoryOperationException("关键词不能为空")

class ReservedCategoryKeywordException(val keyword: String) :
    CategoryOperationException("“$keyword”由睡眠体系处理，不能作为分类关键词")

class CategoryKeywordConflictException(
    val keyword: String,
    val conflictingCategoryId: Long?,
) : CategoryOperationException("关键词“$keyword”已被其他规则使用")

class CategoryKeywordNotFoundException(val keywordRuleId: Long) :
    CategoryOperationException("分类关键词不存在")


package io.github.kovalpv.classbundle.model

/**
 * Тип связи между целевым классом и найденными файлами.
 *
 * Каждый элемент набора `ExportRequest.relations` определяет,
 * какой поиск выполнять в PSI (FR-2…FR-6):
 * - [USAGE] — обращения к классу (`ReferencesSearch`);
 * - [INHERITOR] — наследники и реализаторы (`ClassInheritorsSearch`);
 * - [NESTED] — вложенные классы и их содержимое (`PsiUtil.getNestedClasses`).
 */
enum class RelationType {
    USAGE,
    INHERITOR,
    NESTED,
}

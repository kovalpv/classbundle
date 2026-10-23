package io.github.kovalpv.classbundle.model

/**
 * Тип связи между целевым классом и найденными файлами.
 *
 * Каждый элемент набора [ExportRequest.relations] определяет,
 * какой поиск выполнять в PSI (FR-2…FR-6):
 *
 * - [USAGE] — обращения к классу (`ReferencesSearch`);
 * - [INHERITOR] — наследники и реализаторы (`ClassInheritorsSearch`);
 * - [NESTED] — вложенные классы и их содержимое (`PsiUtil.getNestedClasses`);
 * - [DEPENDENCY] — прямые зависимости целевого класса (один уровень);
 * - [TRANSITIVE_DEPENDENCY] — транзитивные зависимости (BFS по графу
 *   ссылок). Включает прямые зависимости как подмножество, поэтому если
 *   выбрана эта связь, [DEPENDENCY] отдельно не выполняется.
 */
enum class RelationType {
    USAGE,
    INHERITOR,
    NESTED,
    DEPENDENCY,
    TRANSITIVE_DEPENDENCY,
}
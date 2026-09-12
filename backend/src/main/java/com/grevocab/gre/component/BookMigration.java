package com.grevocab.gre.component;

import com.grevocab.gre.mapper.BookMapper;
import com.grevocab.gre.mapper.LessonMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * 应用启动时的书本层数据迁移：
 * 若数据库还没有书本，则自动创建默认书本「GRE 词汇」，
 * 并把没有归属书本的现有课时全部归入该默认书本。
 * 幂等，可重复执行，对已有数据无破坏。
 */
@Component
public class BookMigration {

    private final BookMapper bookMapper;
    private final LessonMapper lessonMapper;

    public BookMigration(BookMapper bookMapper, LessonMapper lessonMapper) {
        this.bookMapper = bookMapper;
        this.lessonMapper = lessonMapper;
    }

    @PostConstruct
    public void migrate() {
        try {
            if (bookMapper.findAll().isEmpty()) {
                com.grevocab.gre.entity.Book book = new com.grevocab.gre.entity.Book();
                book.setTitle("GRE 词汇");
                book.setDescription("系统默认书本，自动收纳未归类的课时");
                book.setSortNo(1);
                book.setStatus(1);
                bookMapper.insert(book);

                // 把现有无归属课时归到默认书本
                lessonMapper.assignBookToDefault(book.getId());
            }
        } catch (Exception e) {
            // 启动期迁移失败不应阻断应用启动；管理员可手动执行 schema-migration.sql
            System.err.println("[BookMigration] 自动迁移失败（可忽略，请手动执行 schema-migration.sql）：" + e.getMessage());
        }
    }
}

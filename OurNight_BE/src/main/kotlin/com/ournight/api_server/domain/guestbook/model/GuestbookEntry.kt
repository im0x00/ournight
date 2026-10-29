package com.ournight.api_server.domain.guestbook.model

import com.ournight.api_server.domain.store.domain.Store
import com.ournight.api_server.domain.user.model.User
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "guestbook_entries")
class GuestbookEntry(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "UUID")
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false, columnDefinition = "UUID")
    val store: Store,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, columnDefinition = "UUID")
    val user: User,

    @Column(name = "author_snapshot", nullable = false, length = 100)
    val authorSnapshot: String,

    @Column(name = "content", nullable = false, length = 120)
    val content: String,

    @ElementCollection
    @CollectionTable(name = "guestbook_entry_tags", joinColumns = [JoinColumn(name = "entry_id")])
    @Column(name = "tag", nullable = false, length = 30)
    val tags: MutableList<String> = mutableListOf(),

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime? = null,
)

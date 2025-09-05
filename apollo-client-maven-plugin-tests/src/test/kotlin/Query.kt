package com.lahzouz.java.graphql.client.tests

import com.coxautodev.graphql.tools.GraphQLQueryResolver

class Query : GraphQLQueryResolver {
    private val books: List<Book>
    private val authors: List<Author>

    init {
        val dickens = Author(name = "Charles Dickens")
        val twain = Author(name = "Mark Twain")
        authors = listOf(dickens, twain)
        books =
            listOf(
                Book(title = "A Christmas Carol", author = dickens, id = 1L),
                Book(title = "David Copperfield", author = dickens, id = 2L),
                Book(title = "The Adventures of Tom Sawyer", author = twain, id = 3L),
                Book(title = "Adventures of Huckleberry Finn", author = twain, id = 4L),
            )
    }

    data class Book(
        val title: String,
        val author: Author,
        val id: Long,
    )

    data class Author(
        val name: String,
    )
}

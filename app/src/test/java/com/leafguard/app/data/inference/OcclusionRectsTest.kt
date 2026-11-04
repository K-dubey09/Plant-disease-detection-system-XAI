package com.leafguard.app.data.inference

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OcclusionRectsTest {

    @Test
    fun count_equalsGridSquared() {
        assertThat(occlusionRects(224, 224, 8)).hasSize(64)
        assertThat(occlusionRects(100, 200, 4)).hasSize(16)
    }

    @Test
    fun index_isRowMajor() {
        val rects = occlusionRects(80, 80, 4)
        assertThat(rects.map { it.index }).containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15).inOrder()
        assertThat(rects[0].gx).isEqualTo(0)
        assertThat(rects[0].gy).isEqualTo(0)
        assertThat(rects[1].gx).isEqualTo(1) // second cell is the next column in row 0
        assertThat(rects[1].gy).isEqualTo(0)
    }

    @Test
    fun cells_areNonOverlappingAndCoverImage() {
        val w = 240
        val h = 120
        val grid = 4
        val rects = occlusionRects(w, h, grid)
        // row 0 cells: x0..x1 should tile [0,w); check the first row's x-ranges.
        val row0 = rects.filter { it.gy == 0 }
        assertThat(row0.map { it.x0 }).containsExactly(0, 60, 120, 180).inOrder()
        assertThat(row0.map { it.x1 }).containsExactly(60, 120, 180, 240).inOrder()
        // every cell has positive area
        rects.forEach { r -> assertThat(r.x1 - r.x0).isGreaterThan(0); assertThat(r.y1 - r.y0).isGreaterThan(0) }
        // union covers the image: max x1 == w, max y1 == h, min x0/y0 == 0
        assertThat(rects.maxOf { it.x1 }).isEqualTo(w)
        assertThat(rects.maxOf { it.y1 }).isEqualTo(h)
        assertThat(rects.minOf { it.x0 }).isEqualTo(0)
        assertThat(rects.minOf { it.y0 }).isEqualTo(0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsZeroGrid() {
        occlusionRects(10, 10, 0)
    }
}

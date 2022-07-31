package tetromino

import rudiments.*
import scala.collection.mutable as scm
import java.lang.ref as jlr

case class OverallocationError(rubrics: Rubric*)(using Codepoint)
extends Error(err"attempted to allocated more memory than available for $rubrics")(pos)

package allocators:
  given default: Allocator = rubric => 10.mb

case class Rubric()
object Total extends Rubric()

abstract class Allocator():
  private var allocations: scm.Map[Rubric, ByteSize] = scm.HashMap().withDefault(0.b.waive)
  private var weakRefs: scm.Map[jlr.Reference[?], (ByteSize, Set[Rubric])] = scm.HashMap()
  private val queue: jlr.ReferenceQueue[Array[Byte]] = jlr.ReferenceQueue()
  @volatile private var continue = false
 
  @tailrec
  private def process(): Unit =
    synchronized:
      Option(queue.remove(100)).map(_.nn).foreach: ref =>
        val (size, rubrics) = weakRefs(ref)
  
        rubrics.foreach: rubric =>
          allocations(rubric) -= size
    
    if continue then process()

  def release(rubric: Rubric, size: ByteSize): Unit = synchronized:
    allocations(rubric) -= size
  
  def limit(rubric: Rubric): ByteSize

  def allocate(size: ByteSize, rubrics: Rubric*): Array[Byte] throws OverallocationError = synchronized:
    val excess = rubrics.filter: rubric =>
      allocations(rubric) + size > limit(rubric)
    
    if !allocations.isEmpty then throw OverallocationError(excess*) else
      val array = new Array[Byte](size.long.toInt)
      val ref = jlr.WeakReference(array, queue)
      weakRefs(ref) = size -> rubrics.to(Set)
      rubrics.foreach(allocations(_) += size)
      array

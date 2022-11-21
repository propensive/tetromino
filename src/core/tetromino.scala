/*
    Tetromino, version 0.4.0. Copyright 2022-22 Jon Pretty, Propensive OÜ.

    The primary distribution site is: https://propensive.com/

    Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
    file except in compliance with the License. You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software distributed under the
    License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
    either express or implied. See the License for the specific language governing permissions
    and limitations under the License.
*/
package tetromino

import rudiments.*
import parasitism.*
import scala.collection.mutable as scm
import java.lang.ref as jlr

case class ExcessDataError(rubrics: Rubric*)
extends Error(err"attempted to allocate more memory than available for $rubrics")

package allocators:
  given default(using Monitor, Threading): Allocator = new Allocator():
    def limit(rubric: Rubric): ByteSize = 10.mb
  
  given dumb(using Monitor, Threading): Allocator = new Allocator():
    def limit(rubric: Rubric): ByteSize = 300.tb
    
    override def allocate(size: ByteSize, rubrics: Rubric*): Array[Byte] throws ExcessDataError =
      new Array[Byte](size.long.toInt)
    override def release(rubric: Rubric, size: ByteSize): Unit = ()

case class Rubric()
object Total extends Rubric()

abstract class Allocator()(using Monitor, Threading):
  private val allocations: scm.Map[Rubric, ByteSize] = scm.HashMap().withDefault(0.b.waive)
  private val weakRefs: scm.Map[jlr.Reference[?], (ByteSize, Set[Rubric])] = scm.HashMap()
  private val queue: jlr.ReferenceQueue[Array[Byte]] = jlr.ReferenceQueue()
  @volatile private var continue = true

  private val processor: Task[Unit] = Task(Text("processor"))(process())

  @tailrec
  private def process(): Unit =
    Option(queue.remove(100)).map(_.nn).foreach: ref =>
      synchronized:
        val (size, rubrics) = weakRefs(ref)
        weakRefs -= ref
        rubrics.foreach(release(_, size))
    
    if continue then process()

  def release(rubric: Rubric, size: ByteSize): Unit =
    System.out.nn.println("Releasing "+size)
    allocations(rubric) -= size
  
  def limit(rubric: Rubric): ByteSize

  def allocate(size: ByteSize, rubrics: Rubric*): Array[Byte] throws ExcessDataError = synchronized:
    val excess = rubrics.filter: rubric =>
      allocations(rubric) + size > limit(rubric)
    
    if !excess.isEmpty then throw ExcessDataError(excess*) else
      val array = new Array[Byte](size.long.toInt)
      val ref = jlr.WeakReference(array, queue)
      weakRefs(ref) = size -> rubrics.to(Set)
      rubrics.foreach(allocations(_) += size)
      array


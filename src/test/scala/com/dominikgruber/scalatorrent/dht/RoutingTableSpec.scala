package com.dominikgruber.scalatorrent.dht

import com.dominikgruber.scalatorrent.dht.RoutingTable._
import com.dominikgruber.scalatorrent.util.UnitSpec
import org.scalatest.PrivateMethodTester
import Util._
import com.dominikgruber.scalatorrent.dht.DhtMessage.NodeId

import scala.collection.SortedMap

/**
  * https://blog.maidsafe.net/2016/05/27/structuring-networks-with-xor/
  */
class RoutingTableSpec extends UnitSpec with PrivateMethodTester {

  it should "create the first bucket" in {
    val table = RoutingTable(node("0A"), 4)
    table.buckets shouldBe Map(Min -> Bucket(Map.empty, Min, Max))
  }

  it should "add a node" in {
    val table = RoutingTable(node("0A"), 4)
    val before = System.currentTimeMillis
    table.add(node("01"))
    val after = System.currentTimeMillis

    table.buckets.size shouldBe 1
    val (_, Bucket(nodes, _, _)) = table.buckets.head
    nodes.size shouldBe 1
    val (id, status) = nodes.head
    id shouldBe node("01")
    status.quality shouldBe Good
    status.lastAcive should (be >= before and be <= after)
  }

  it should "fit k nodes in the 1st bucket" in {
    val table = RoutingTable(node("00"), 4)
    table.add(node("01"))
    table.add(node("02"))
    table.add(node("03"))
    table.add(node("04"))

    table.buckets.size shouldBe 1
    val (_, Bucket(nodes, _, _)) = table.buckets.head
    nodes.size shouldBe 4
  }

  it should "split the 1st bucket in two" in {
    val me = node("80")             //- - o -
    val table = RoutingTable(me, 2)
    val node1 = add(table, "01", 1) //o - - -
    val node2 = add(table, "02", 1) //o - - -
    val node3 = add(table, "FF", 2) //- -|- o <- should split

    val (key1, bucket1) :: (key2, bucket2) :: Nil = table.buckets.toList

    val middle = (Min + Max) / 2
    key1 shouldBe Min
    key2 shouldBe middle
    bucket1.min shouldBe Min
    bucket1.max shouldBe middle
    bucket2.min shouldBe middle
    bucket2.max shouldBe Max

    bucket1.nodes.keySet shouldBe Set(node1, node2)
    bucket2.nodes.keySet shouldBe Set(node3)
  }

  it should "not split if all nodes still fall in one bucket" in {
    val me = node("00")             //o - - -
    val table = RoutingTable(me, 2)
    val node1 = add(table, "01", 1) //o - - -
    val node2 = add(table, "02", 1) //o - - -
    val node3 = add(table, "03", 1) //x - - - <- should discard

    val bucket = table.buckets(Min)
    bucket.nodes.keySet shouldBe Set(node1, node2)
  }

  it should "split the middle bucket out of three" in {
    val me = node("80")              //- - - - o - - -
    val table = RoutingTable(me, 2)
    val node1a = add(table, "01", 1) //o - - - - - - -
    val node1b = add(table, "02", 1) //o - - - - - - -
    val node2a = add(table, "F1", 2) //- - - -|- - - o
    val node2b = add(table, "F2", 2) //- - - -|- - - o
    val node3a = add(table, "A1", 3) //- - - -|- o|- -
    val node3b = add(table, "A2", 3) //- - - -|- o|- -
    val node4  = add(table, "81", 4) //- - - -|o|-|- - <- should split

    val (_, b1) :: (_, b2) :: (_, b3) :: (_, b4) :: Nil = table.buckets.toList
    b1.nodes.keySet shouldBe Set(node1a, node1b)
    b2.nodes.keySet shouldBe Set(node4)
    b3.nodes.keySet shouldBe Set(node3a, node3b)
    b4.nodes.keySet shouldBe Set(node2a, node2b)
  }

  object Scenario {
    val me = node("80")              //- - - - o - - -
    val table = RoutingTable(me, 2)
    val nodeD1 = add(table, "01", 1) //o - - - - - - -
    val nodeD2 = add(table, "02", 1) //o - - - - - - -
    val nodeC1 = add(table, "F1", 2) //- - - -|- - - o
    val nodeC2 = add(table, "F2", 2) //- - - -|- - - o
    val nodeB1 = add(table, "A1", 3) //- - - -|- o|- -
    val nodeB2 = add(table, "A2", 3) //- - - -|- o|- -
    val nodeA1 = add(table, "81", 4) //- - - -|o|-|- -
                                     //   D    A B  C
  }

  //TODO should discard a bad node

  it should "find the closest node" in {
    val table = Scenario.table
    table.findClosestNode(hash("01")) shouldBe Some(Scenario.nodeD1)
    table.findClosestNode(hash("7F")) shouldBe Some(Scenario.nodeD2)
    table.findClosestNode(hash("FF")) shouldBe Some(Scenario.nodeC2)
    table.findClosestNode(hash("80")) shouldBe Some(Scenario.nodeA1)
  }

  /**
    * Adds a node and checks the expected buckets count
    */
  def add(table: RoutingTable, nodeId: String, expectedBuckets: Int): NodeId = {
    val newNode = node(nodeId)
    table.add(newNode)
    table.buckets.size shouldBe expectedBuckets
    newNode
  }

  type Buckets = SortedMap[BigInt, Bucket]
  implicit class WhiteBox(sut: RoutingTable) {
    def buckets: Buckets = sut invokePrivate PrivateMethod[Buckets]('buckets)()
  }

}

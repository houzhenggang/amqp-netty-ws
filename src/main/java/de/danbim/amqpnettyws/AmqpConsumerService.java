package de.danbim.amqpnettyws;

import com.google.common.eventbus.EventBus;
import com.google.common.util.concurrent.AbstractService;
import com.rabbitmq.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class AmqpConsumerService extends AbstractService {

	private static final Logger log = LoggerFactory.getLogger(AmqpConsumerService.class);

	private final EventBus eventBus;

	private final AmqpNettyWsConfig config;

	private Connection brokerConnection;

	private Channel brokerChannel;

	public AmqpConsumerService(final EventBus eventBus, final AmqpNettyWsConfig config) {
		this.eventBus = eventBus;
		this.config = config;
	}

	@Override
	protected void doStart() {

		ConnectionFactory factory = new ConnectionFactory();
		factory.setHost(config.brokerHost);
		factory.setPort(config.brokerPort);
		factory.setUsername(config.brokerUsername);
		factory.setPassword(config.brokerPassword);
		factory.setVirtualHost(config.brokerVirtualHost);

		try {

			brokerConnection = factory.newConnection();
			brokerChannel = brokerConnection.createChannel();
			brokerChannel.exchangeDeclare(
					config.brokerExchangeName,
					config.brokerExchangeType,
					config.brokerExchangeDurable
			);
			String queueName = brokerChannel.queueDeclare().getQueue();
			brokerChannel.queueBind(
					queueName,
					config.brokerExchangeName,
					config.brokerRoutingKey
			);
			brokerChannel.basicConsume(queueName, true, new DefaultConsumer(brokerChannel) {
				@Override
				public void handleDelivery(final String consumerTag, final Envelope envelope,
										   final AMQP.BasicProperties properties, final byte[] body)
						throws IOException {
					eventBus.post(body);
				}
			}
			);

		} catch (IOException e) {
			notifyFailed(e);
			return;
		}

		notifyStarted();
	}

	@Override
	protected void doStop() {

		if (brokerChannel != null) {
			try {
				brokerChannel.close();
			} catch (IOException e) {
				log.warn("{}", e);
			}
		}

		if (brokerConnection != null) {
			try {
				brokerConnection.close();
			} catch (IOException e) {
				log.warn("{}", e);
			}
		}

		notifyStopped();
	}
}
